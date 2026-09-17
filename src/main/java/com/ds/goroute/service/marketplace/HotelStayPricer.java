package com.ds.goroute.service.marketplace;

import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RatePlanPromotion;
import com.ds.goroute.repository.MarketplacePromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prices the nights of a stay. The single place that turns a rate plan, its calendar and its
 * promotions into money, so the booking, the cart quote and the guest-facing offers can never
 * disagree on a price.
 */
@Component
@RequiredArgsConstructor
public class HotelStayPricer {
    private final MarketplacePromotionRepository promotionRepository;
    private final MarketplaceJson json;

    /** Gross price per night for the whole party, then at most one promotion applied to each night. */
    public List<PromotionEngine.AppliedNight> priceNights(UUID hotelId, RatePlan rate, List<HotelAvailabilityDay> days,
                                                          int quantity, int adults, int children, int nights,
                                                          LocalDate checkIn, LocalDate today) {
        List<PromotionEngine.Promotion> promotions = enginePromotions(hotelId, rate.getId(), checkIn, checkIn.plusDays(nights), today);
        return days.stream().map(day -> {
            BigDecimal nightly = day.getNightlyPrice() == null ? rate.getBasePrice() : day.getNightlyPrice();
            BigDecimal gross = nightlyQuote(rate, nightly, quantity, adults, children, nights);
            return PromotionEngine.price(promotions, rate.getId(), day.getInventoryDate(), gross, today, checkIn, nights);
        }).toList();
    }

    private List<PromotionEngine.Promotion> enginePromotions(UUID hotelId, UUID ratePlanId, LocalDate checkIn, LocalDate checkOut, LocalDate today) {
        List<RatePlanPromotion> rows = promotionRepository.findApplicable(hotelId, ratePlanId, checkIn, checkOut, today);
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream().map(p -> new PromotionEngine.Promotion(p.getId(), p.getRatePlanId(), p.getCode(), p.getName(), p.getPromotionType(),
                p.getDiscountPercent(), p.getPriority() == null ? 100 : p.getPriority(), p.getStayStart(), p.getStayEnd(), p.getBookStart(), p.getBookEnd(),
                p.getMinAdvanceDays(), p.getMaxAdvanceDays(), p.getMinNights(),
                json.readList(p.getDaysOfWeek(), String.class).stream().map(d -> DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT))).collect(Collectors.toSet()),
                p.getCreatedAt() == null ? null : p.getCreatedAt().toLocalDate())).toList();
    }

    /**
     * One night for the whole party.
     *
     * <p>{@code occupancy_pricing} is read according to {@code pricing_model}: for
     * {@code OCCUPANCY_BASED} it maps adults-per-room to the room price (and then the extra-adult fee is
     * not charged on top, because the table already priced that occupancy); for {@code LENGTH_OF_STAY} it
     * maps a minimum number of nights to a percentage off. {@code DERIVED} is rejected when a rate is saved.
     */
    private BigDecimal nightlyQuote(RatePlan rate, BigDecimal nightlyPrice, int rooms, int adults, int children, int nights) {
        BigDecimal price = nightlyPrice == null ? rate.getBasePrice() : nightlyPrice;
        String model = rate.getPricingModel() == null ? "STANDARD" : rate.getPricingModel();
        Map<String, Object> table = json.readMap(rate.getOccupancyPricing());
        boolean occupancyPriced = false;
        if ("OCCUPANCY_BASED".equals(model) && !table.isEmpty()) {
            int adultsPerRoom = (int) Math.ceil(adults / (double) Math.max(1, rooms));
            BigDecimal occupancyPrice = decimalOrNull(table.get(String.valueOf(adultsPerRoom)));
            if (occupancyPrice != null) {
                price = occupancyPrice;
                occupancyPriced = true;
            }
        }
        BigDecimal total = price.multiply(BigDecimal.valueOf(rooms));
        int base = (rate.getBaseOccupancy() == null ? 1 : rate.getBaseOccupancy()) * rooms;
        int extraAdults = occupancyPriced ? 0 : Math.max(0, adults - base);
        int extraChildren = Math.max(0, children);
        if (rate.getExtraAdultFee() != null) total = total.add(rate.getExtraAdultFee().multiply(BigDecimal.valueOf(extraAdults)));
        if (rate.getExtraChildFee() != null) total = total.add(rate.getExtraChildFee().multiply(BigDecimal.valueOf(extraChildren)));
        if ("LENGTH_OF_STAY".equals(model) && !table.isEmpty()) {
            BigDecimal percent = BigDecimal.ZERO;
            for (Map.Entry<String, Object> entry : table.entrySet()) {
                Integer threshold = intOrNull(entry.getKey());
                BigDecimal value = decimalOrNull(entry.getValue());
                if (threshold != null && value != null && nights >= threshold && value.compareTo(percent) > 0) percent = value;
            }
            if (percent.signum() > 0) {
                total = total.multiply(BigDecimal.valueOf(100).subtract(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The VAT and service-charge share of a tax-inclusive total: service charge applies to the net price and
     * VAT to net plus service charge, the Vietnamese invoicing order. Null when the property declares neither.
     */
    public static BigDecimal includedTaxes(BigDecimal total, BigDecimal vatPercent, BigDecimal serviceChargePercent) {
        if (total == null || (vatPercent == null && serviceChargePercent == null)) return null;
        BigDecimal factor = multiplier(serviceChargePercent).multiply(multiplier(vatPercent));
        BigDecimal net = total.divide(factor, 2, RoundingMode.HALF_UP);
        return total.subtract(net).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal multiplier(BigDecimal percent) {
        return percent == null ? BigDecimal.ONE : BigDecimal.ONE.add(percent.movePointLeft(2));
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer intOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
