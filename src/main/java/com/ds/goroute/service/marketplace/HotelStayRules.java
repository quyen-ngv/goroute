package com.ds.goroute.service.marketplace;

import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.type.HotelOfferUnavailableReason;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The bookability rules createBooking enforces, as a pure check that answers instead of throwing, so
 * a guest browsing offers sees why a rate is unavailable before trying to book it.
 */
public final class HotelStayRules {
    /** Longest stay a single booking may cover. */
    public static final int MAX_BOOKING_NIGHTS = 90;
    /** Check-in time assumed for cancellation deadlines when the property has not set one. */
    public static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(14, 0);

    private HotelStayRules() {
    }

    public static boolean fits(RoomType room, int rooms, int adults, int children) {
        return adults <= room.getMaxAdults() * rooms
                && children <= room.getMaxChildren() * rooms
                && adults + children <= room.getMaxOccupancy() * rooms;
    }

    /**
     * @param days        calendar rows for the stay, one per night when the rate is open every night
     * @param advanceDays days from the property's today to check-in
     */
    public static Optional<HotelOfferUnavailableReason> violation(List<HotelAvailabilityDay> days, int nights, int rooms, int advanceDays) {
        if (days.size() != nights) return Optional.of(HotelOfferUnavailableReason.NOT_OPEN);
        if (days.stream().anyMatch(d -> Boolean.TRUE.equals(d.getStopSell()) || availableUnits(d) < rooms)) {
            return Optional.of(HotelOfferUnavailableReason.SOLD_OUT);
        }
        if (Boolean.TRUE.equals(days.get(0).getClosedToArrival())) return Optional.of(HotelOfferUnavailableReason.ARRIVAL_CLOSED);
        if (Boolean.TRUE.equals(days.get(nights - 1).getClosedToDeparture())) return Optional.of(HotelOfferUnavailableReason.DEPARTURE_CLOSED);
        int minimum = days.stream().map(HotelAvailabilityDay::getMinStay).filter(Objects::nonNull).max(Integer::compareTo).orElse(1);
        int maximum = days.stream().map(HotelAvailabilityDay::getMaxStay).filter(Objects::nonNull).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        if (nights < minimum || nights > maximum) return Optional.of(HotelOfferUnavailableReason.STAY_LENGTH);
        int minimumAdvance = Optional.ofNullable(days.get(0).getMinAdvanceDays()).orElse(0);
        Integer maximumAdvance = days.get(0).getMaxAdvanceDays();
        if (advanceDays < minimumAdvance || (maximumAdvance != null && advanceDays > maximumAdvance)) {
            return Optional.of(HotelOfferUnavailableReason.BOOKING_WINDOW);
        }
        return Optional.empty();
    }

    /** Fewest units free on any night of the stay. */
    public static int minimumAvailableUnits(List<HotelAvailabilityDay> days) {
        return days.stream().mapToInt(HotelStayRules::availableUnits).min().orElse(0);
    }

    private static int availableUnits(HotelAvailabilityDay day) {
        return day.getAvailableUnits() == null ? 0 : day.getAvailableUnits();
    }
}
