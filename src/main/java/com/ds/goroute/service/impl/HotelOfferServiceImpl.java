package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.HotelOfferQuery;
import com.ds.goroute.dto.response.HotelCancellationTermsResponse;
import com.ds.goroute.dto.response.HotelRateOfferResponse;
import com.ds.goroute.dto.response.HotelRoomOfferResponse;
import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.service.HotelOfferService;
import com.ds.goroute.service.marketplace.CancellationPolicyEvaluator;
import com.ds.goroute.service.marketplace.HotelCatalogAssembler;
import com.ds.goroute.service.marketplace.HotelStayPricer;
import com.ds.goroute.service.marketplace.HotelStayRules;
import com.ds.goroute.service.marketplace.MarketplaceDisplayPrice;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.service.marketplace.PromotionEngine;
import com.ds.goroute.type.HotelOfferUnavailableReason;
import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices every enabled room type and rate plan of a hotel for one stay, with the same pricer and
 * rules a booking uses. Costs two queries per rate plan (calendar and promotions), which is bounded
 * by the handful of rates a property sells.
 */
@Service
@RequiredArgsConstructor
public class HotelOfferServiceImpl implements HotelOfferService {
    private static final int MAX_ROOMS = 10;
    private static final int MAX_GUESTS = 50;

    private final HotelMarketplaceRepository repository;
    private final HotelStayPricer pricer;
    private final HotelCatalogAssembler catalogAssembler;
    private final MarketplaceDisplayPrice displayPrice;
    private final MarketplaceJson json;

    @Override
    public List<HotelRoomOfferResponse> listOffers(UUID hotelId, HotelOfferQuery query) {
        HotelProfile hotel = repository.findPublicHotel(hotelId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Hotel not found"));
        ZoneId zone = zoneOf(hotel);
        Stay stay = stayOf(query, LocalDate.now(zone), zone);
        return repository.findRoomTypes(hotelId, false).stream()
                .filter(room -> MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus()))
                .map(room -> roomOffer(hotel, room, stay))
                .toList();
    }

    private HotelRoomOfferResponse roomOffer(HotelProfile hotel, RoomType room, Stay stay) {
        List<RatePlan> rates = repository.findRatePlans(room.getId(), false).stream()
                .filter(rate -> MarketplaceAvailabilityStatus.ENABLED.name().equals(rate.getStatus()))
                .toList();
        List<HotelRateOfferResponse> offers = rates.stream()
                .map(rate -> rateOffer(hotel, room, rate, stay))
                .sorted(Comparator.comparing(HotelRateOfferResponse::available).reversed()
                        .thenComparing(HotelRateOfferResponse::nightlyAveragePrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Optional<HotelRateOfferResponse> cheapest = offers.stream().filter(HotelRateOfferResponse::available).findFirst();
        Optional<RatePlan> cheapestBase = rates.stream().min(Comparator.comparing(RatePlan::getBasePrice));
        return HotelRoomOfferResponse.builder()
                .room(catalogAssembler.toRoomResponse(room))
                .available(cheapest.isPresent())
                .fromNightlyPrice(cheapest.map(HotelRateOfferResponse::nightlyAveragePrice)
                        .orElseGet(() -> cheapestBase.map(r -> displayPrice.convert(r.getBasePrice(), r.getCurrency())).orElse(null)))
                .currency(cheapest.map(HotelRateOfferResponse::currency)
                        .orElseGet(() -> cheapestBase.map(r -> displayPrice.currencyOf(r.getCurrency())).orElse(null)))
                .rates(offers)
                .build();
    }

    private HotelRateOfferResponse rateOffer(HotelProfile hotel, RoomType room, RatePlan rate, Stay stay) {
        String stored = rate.getCurrency();
        HotelRateOfferResponse.HotelRateOfferResponseBuilder offer = HotelRateOfferResponse.builder()
                .ratePlan(catalogAssembler.displayed(catalogAssembler.toRateResponse(rate)))
                .nights(stay.nights()).currency(displayPrice.currencyOf(stored));
        if (!HotelStayRules.fits(room, stay.rooms(), stay.adults(), stay.children())) {
            return unavailable(offer, HotelOfferUnavailableReason.CAPACITY);
        }
        List<HotelAvailabilityDay> days = repository.findAvailability(hotel.getId(), room.getId(), rate.getId(),
                stay.checkIn(), stay.checkOut(), stay.today());
        if (days.size() == stay.nights()) offer.availableUnits(HotelStayRules.minimumAvailableUnits(days));
        Optional<HotelOfferUnavailableReason> violation = HotelStayRules.violation(days, stay.nights(), stay.rooms(), stay.advanceDays());
        if (violation.isPresent()) return unavailable(offer, violation.get());

        List<PromotionEngine.AppliedNight> priced = pricer.priceNights(hotel.getId(), rate, days, stay.rooms(),
                stay.adults(), stay.children(), stay.nights(), stay.checkIn(), stay.today());
        BigDecimal total = sum(priced.stream().map(PromotionEngine.AppliedNight::price).toList());
        BigDecimal original = sum(priced.stream().map(PromotionEngine.AppliedNight::originalPrice).toList());
        BigDecimal nightly = total.divide(BigDecimal.valueOf(stay.nights()), 2, RoundingMode.HALF_UP);
        Optional<PromotionEngine.AppliedNight> promotion = priced.stream().filter(PromotionEngine.AppliedNight::discounted).findFirst();
        return offer.available(true)
                .totalPrice(displayPrice.convert(total, stored))
                .originalTotalPrice(promotion.isPresent() ? displayPrice.convert(original, stored) : null)
                .nightlyAveragePrice(displayPrice.convert(nightly, stored))
                .promotionCode(promotion.map(PromotionEngine.AppliedNight::code).orElse(null))
                .promotionPercent(promotion.map(PromotionEngine.AppliedNight::percent).orElse(null))
                .taxesAndFeesAmount(displayPrice.convert(HotelStayPricer.includedTaxes(total, hotel.getVatPercent(), hotel.getServiceChargePercent()), stored))
                .cancellation(cancellationTerms(hotel, rate, stay, total, nightly))
                .build();
    }

    /**
     * The policy evaluated just after check-in gives the penalty that applies once the free window is
     * over; its {@code freeUntil} is offered only while it is still ahead of the guest.
     */
    private HotelCancellationTermsResponse cancellationTerms(HotelProfile hotel, RatePlan rate, Stay stay,
                                                             BigDecimal total, BigDecimal firstNight) {
        LocalDateTime start = LocalDateTime.of(stay.checkIn(),
                Objects.requireNonNullElse(hotel.getCheckInTime(), HotelStayRules.DEFAULT_CHECK_IN_TIME));
        CancellationPolicyEvaluator.Outcome late = CancellationPolicyEvaluator.evaluate(
                json.readMap(rate.getCancellationPolicy()), start, start.plusSeconds(1), total, firstNight);
        boolean refundable = !CancellationPolicyEvaluator.TYPE_NON_REFUNDABLE.equals(late.policyType())
                && !Boolean.FALSE.equals(rate.getRefundable());
        LocalDateTime freeUntil = refundable && late.freeUntil() != null && stay.now().isBefore(late.freeUntil())
                ? late.freeUntil() : null;
        return HotelCancellationTermsResponse.builder()
                .policyType(late.policyType()).refundable(refundable).freeUntil(freeUntil)
                .penaltyAmount(displayPrice.convert(late.penaltyAmount(), rate.getCurrency()))
                .penaltyRule(late.penaltyRule())
                .build();
    }

    private static HotelRateOfferResponse unavailable(HotelRateOfferResponse.HotelRateOfferResponseBuilder offer,
                                                      HotelOfferUnavailableReason reason) {
        return offer.available(false).unavailableReason(reason.name()).build();
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static Stay stayOf(HotelOfferQuery q, LocalDate today, ZoneId zone) {
        if (q.checkIn() == null || q.checkOut() == null || !q.checkOut().isAfter(q.checkIn()) || q.checkIn().isBefore(today)) {
            throw badRequest("Invalid check-in/check-out dates");
        }
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(q.checkIn(), q.checkOut()));
        if (nights > HotelStayRules.MAX_BOOKING_NIGHTS) throw badRequest("Maximum stay is " + HotelStayRules.MAX_BOOKING_NIGHTS + " nights");
        if (q.rooms() < 1 || q.rooms() > MAX_ROOMS || q.adults() < 1 || q.children() < 0 || q.adults() + q.children() > MAX_GUESTS) {
            throw badRequest("Invalid room or guest count");
        }
        int advanceDays = Math.toIntExact(ChronoUnit.DAYS.between(today, q.checkIn()));
        return new Stay(q.checkIn(), q.checkOut(), nights, q.rooms(), q.adults(), q.children(), today, LocalDateTime.now(zone), advanceDays);
    }

    /** "Today" at the property, not on the server. */
    private static ZoneId zoneOf(HotelProfile hotel) {
        try {
            return ZoneId.of(hotel.getTimezone());
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ErrorConstant.BAD_REQUEST, message);
    }

    private record Stay(LocalDate checkIn, LocalDate checkOut, int nights, int rooms, int adults, int children,
                        LocalDate today, LocalDateTime now, int advanceDays) {
    }
}
