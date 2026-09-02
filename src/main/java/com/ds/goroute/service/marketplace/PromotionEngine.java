package com.ds.goroute.service.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Picks the promotion that applies to one night and prices it.
 *
 * <p>Deliberately <b>never stacks</b>: a night gets at most one promotion, the one with the highest
 * discount, ties broken by the lower {@code priority} number and then by the oldest promotion. This is
 * the rule Booking.com and Airbnb both settled on, and it is the only one a partner can reason about —
 * "why is this night 43% off" has exactly one answer.
 *
 * <p>Pure: no clock, no repository. The caller passes "today" in the property's timezone.
 */
public final class PromotionEngine {
    private PromotionEngine() {}

    /** A promotion as the engine needs it; the entity keeps the storage shape. */
    public record Promotion(UUID id, UUID ratePlanId, String code, String name, String type,
                            BigDecimal discountPercent, int priority,
                            LocalDate stayStart, LocalDate stayEnd, LocalDate bookStart, LocalDate bookEnd,
                            Integer minAdvanceDays, Integer maxAdvanceDays, Integer minNights,
                            Set<DayOfWeek> daysOfWeek, LocalDate createdOn) {}

    /** What was applied to one night. {@code price} is what the guest pays for that night. */
    public record AppliedNight(LocalDate date, BigDecimal originalPrice, BigDecimal price,
                               UUID promotionId, String code, BigDecimal percent) {
        public boolean discounted() { return promotionId != null; }
        public BigDecimal discount() { return originalPrice.subtract(price); }
    }

    /**
     * @param promotions candidates already narrowed to the hotel (any rate plan)
     * @param ratePlanId the rate plan being priced; a promotion with a null rate plan applies to all
     * @param night      the night being priced
     * @param bookedOn   the date the booking is made (property timezone)
     * @param checkIn    first night of the stay
     * @param nights     length of the stay
     */
    public static Optional<Promotion> bestFor(List<Promotion> promotions, UUID ratePlanId, LocalDate night,
                                              LocalDate bookedOn, LocalDate checkIn, int nights) {
        if (promotions == null || promotions.isEmpty()) return Optional.empty();
        long advanceDays = checkIn.toEpochDay() - bookedOn.toEpochDay();
        return promotions.stream()
                .filter(p -> p.ratePlanId() == null || p.ratePlanId().equals(ratePlanId))
                .filter(p -> p.discountPercent() != null && p.discountPercent().signum() > 0)
                .filter(p -> p.stayStart() == null || !night.isBefore(p.stayStart()))
                .filter(p -> p.stayEnd() == null || !night.isAfter(p.stayEnd()))
                .filter(p -> p.bookStart() == null || !bookedOn.isBefore(p.bookStart()))
                .filter(p -> p.bookEnd() == null || !bookedOn.isAfter(p.bookEnd()))
                .filter(p -> p.minNights() == null || nights >= p.minNights())
                .filter(p -> p.minAdvanceDays() == null || advanceDays >= p.minAdvanceDays())
                .filter(p -> p.maxAdvanceDays() == null || advanceDays <= p.maxAdvanceDays())
                .filter(p -> p.daysOfWeek() == null || p.daysOfWeek().isEmpty() || p.daysOfWeek().contains(night.getDayOfWeek()))
                .max(Comparator.comparing(Promotion::discountPercent)
                        .thenComparing(Comparator.comparingInt(Promotion::priority).reversed())
                        .thenComparing(p -> p.createdOn() == null ? LocalDate.MAX : p.createdOn(), Comparator.reverseOrder()));
    }

    /** Applies {@link #bestFor} to a night's gross price. */
    public static AppliedNight price(List<Promotion> promotions, UUID ratePlanId, LocalDate night, BigDecimal grossPrice,
                                     LocalDate bookedOn, LocalDate checkIn, int nights) {
        BigDecimal gross = grossPrice == null ? BigDecimal.ZERO : grossPrice;
        Optional<Promotion> best = bestFor(promotions, ratePlanId, night, bookedOn, checkIn, nights);
        if (best.isEmpty()) return new AppliedNight(night, gross, gross, null, null, null);
        Promotion p = best.get();
        BigDecimal keep = BigDecimal.valueOf(100).subtract(p.discountPercent());
        BigDecimal net = gross.multiply(keep).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new AppliedNight(night, gross, net, p.id(), p.code(), p.discountPercent());
    }
}
