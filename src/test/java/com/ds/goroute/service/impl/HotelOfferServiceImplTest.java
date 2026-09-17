package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.HotelOfferQuery;
import com.ds.goroute.dto.response.HotelRateOfferResponse;
import com.ds.goroute.dto.response.HotelRoomOfferResponse;
import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MarketplacePromotionRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.marketplace.HotelCatalogAssembler;
import com.ds.goroute.service.marketplace.HotelStayPricer;
import com.ds.goroute.service.marketplace.MarketplaceDisplayPrice;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotelOfferServiceImplTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HotelMarketplaceRepository repository = mock(HotelMarketplaceRepository.class);
    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID flexibleRateId = UUID.randomUUID();
    private final UUID soldOutRateId = UUID.randomUUID();
    private final LocalDate checkIn = LocalDate.now(ZONE).plusDays(20);
    private HotelOfferServiceImpl service;

    @BeforeEach
    void setUp() {
        MarketplaceJson json = new MarketplaceJson(new ObjectMapper().findAndRegisterModules());
        MarketplaceDisplayPrice displayPrice = new MarketplaceDisplayPrice(mock(ExchangeRateService.class));
        service = new HotelOfferServiceImpl(repository,
                new HotelStayPricer(mock(MarketplacePromotionRepository.class), json),
                new HotelCatalogAssembler(json, mock(PlaceScoreRepository.class), displayPrice),
                displayPrice, json);

        when(repository.findPublicHotel(hotelId)).thenReturn(Optional.of(HotelProfile.builder().id(hotelId)
                .timezone(ZONE.getId()).checkInTime(LocalTime.of(15, 0)).vatPercent(new BigDecimal("10")).build()));
        when(repository.findRoomTypes(hotelId, false)).thenReturn(List.of(RoomType.builder().id(roomId).hotelId(hotelId)
                .name("Deluxe").status("ENABLED").maxAdults(2).maxChildren(1).maxOccupancy(3).totalUnits(5).build()));
        when(repository.findRatePlans(roomId, false)).thenReturn(List.of(
                rate(soldOutRateId, "900000", "{\"type\":\"NON_REFUNDABLE\"}"),
                rate(flexibleRateId, "1100000", "{\"type\":\"FLEXIBLE\",\"freeCancellationHours\":48,\"penaltyType\":\"FIRST_NIGHT\"}")));
        when(repository.findAvailability(eq(hotelId), eq(roomId), eq(flexibleRateId), any(), any(), any()))
                .thenReturn(nights("1100000", 2));
        when(repository.findAvailability(eq(hotelId), eq(roomId), eq(soldOutRateId), any(), any(), any()))
                .thenReturn(nights("900000", 0));
    }

    @Test
    @DisplayName("Prices each rate for the stay, available rates first, with taxes share and cancellation terms")
    void pricesRatesForTheStay() {
        List<HotelRoomOfferResponse> offers = service.listOffers(hotelId, query(1, 2));

        assertThat(offers).hasSize(1);
        HotelRoomOfferResponse room = offers.get(0);
        assertThat(room.available()).isTrue();
        assertThat(room.fromNightlyPrice()).isEqualByComparingTo("1100000");
        assertThat(room.rates()).extracting(r -> r.ratePlan().getId()).containsExactly(flexibleRateId, soldOutRateId);

        HotelRateOfferResponse flexible = room.rates().get(0);
        assertThat(flexible.totalPrice()).isEqualByComparingTo("2200000");
        assertThat(flexible.availableUnits()).isEqualTo(2);
        assertThat(flexible.taxesAndFeesAmount()).isEqualByComparingTo("200000");
        assertThat(flexible.cancellation().refundable()).isTrue();
        assertThat(flexible.cancellation().freeUntil()).isEqualTo(checkIn.minusDays(2).atTime(15, 0));
        assertThat(flexible.cancellation().penaltyAmount()).isEqualByComparingTo("1100000");

        HotelRateOfferResponse soldOut = room.rates().get(1);
        assertThat(soldOut.available()).isFalse();
        assertThat(soldOut.unavailableReason()).isEqualTo("SOLD_OUT");
        assertThat(soldOut.totalPrice()).isNull();
    }

    @Test
    @DisplayName("A party larger than the rooms can hold is reported per rate, not as an error")
    void capacityIsAnAnswer() {
        HotelRoomOfferResponse room = service.listOffers(hotelId, query(1, 4)).get(0);

        assertThat(room.available()).isFalse();
        assertThat(room.rates()).allSatisfy(rate -> assertThat(rate.unavailableReason()).isEqualTo("CAPACITY"));
        assertThat(room.fromNightlyPrice()).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Past check-in dates are rejected")
    void rejectsPastDates() {
        HotelOfferQuery past = HotelOfferQuery.builder().checkIn(LocalDate.now(ZONE).minusDays(1))
                .checkOut(LocalDate.now(ZONE).plusDays(1)).rooms(1).adults(1).children(0).build();
        assertThatThrownBy(() -> service.listOffers(hotelId, past)).isInstanceOf(BusinessException.class);
    }

    private HotelOfferQuery query(int rooms, int adults) {
        return HotelOfferQuery.builder().checkIn(checkIn).checkOut(checkIn.plusDays(2)).rooms(rooms).adults(adults).children(0).build();
    }

    private RatePlan rate(UUID id, String price, String cancellationPolicy) {
        return RatePlan.builder().id(id).roomTypeId(roomId).name("Rate " + price).status("ENABLED").currency("VND")
                .basePrice(new BigDecimal(price)).pricingModel("STANDARD").baseOccupancy(2).minStay(1)
                .cancellationPolicy(cancellationPolicy).refundable(!cancellationPolicy.contains("NON_REFUNDABLE")).build();
    }

    private List<HotelAvailabilityDay> nights(String price, int units) {
        return List.of(night(0, price, units), night(1, price, units));
    }

    private HotelAvailabilityDay night(int offset, String price, int units) {
        return HotelAvailabilityDay.builder().inventoryDate(checkIn.plusDays(offset)).availableUnits(units)
                .stopSell(false).closedToArrival(false).closedToDeparture(false).minStay(1)
                .nightlyPrice(new BigDecimal(price)).build();
    }
}
