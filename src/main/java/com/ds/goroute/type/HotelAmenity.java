package com.ds.goroute.type;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Property-level amenity codes. {@code hotel_profiles.amenities} stores these names; a value
 * that is not a known code is kept as partner free text and grouped under {@link HotelAmenityGroup#OTHER}.
 * {@code popular} marks the handful shown with an icon at the top of the facilities section.
 */
public enum HotelAmenity {
    WIFI(HotelAmenityGroup.GENERAL, true),
    AIR_CONDITIONING(HotelAmenityGroup.GENERAL, false),
    HEATING(HotelAmenityGroup.GENERAL, false),
    ELEVATOR(HotelAmenityGroup.GENERAL, false),
    NON_SMOKING_ROOMS(HotelAmenityGroup.GENERAL, false),
    DESIGNATED_SMOKING_AREA(HotelAmenityGroup.GENERAL, false),
    GARDEN(HotelAmenityGroup.GENERAL, false),
    TERRACE(HotelAmenityGroup.GENERAL, false),

    RESTAURANT(HotelAmenityGroup.FOOD_DRINK, true),
    BAR(HotelAmenityGroup.FOOD_DRINK, false),
    BREAKFAST_AVAILABLE(HotelAmenityGroup.FOOD_DRINK, false),
    ROOM_SERVICE(HotelAmenityGroup.FOOD_DRINK, false),
    SHARED_KITCHEN(HotelAmenityGroup.FOOD_DRINK, false),

    SWIMMING_POOL(HotelAmenityGroup.WELLNESS, true),
    SPA(HotelAmenityGroup.WELLNESS, false),
    FITNESS_CENTER(HotelAmenityGroup.WELLNESS, true),
    SAUNA(HotelAmenityGroup.WELLNESS, false),
    BEACH_ACCESS(HotelAmenityGroup.WELLNESS, false),

    FRONT_DESK_24H(HotelAmenityGroup.SERVICES, true),
    LUGGAGE_STORAGE(HotelAmenityGroup.SERVICES, true),
    LAUNDRY(HotelAmenityGroup.SERVICES, false),
    CONCIERGE(HotelAmenityGroup.SERVICES, false),
    CURRENCY_EXCHANGE(HotelAmenityGroup.SERVICES, false),
    TOUR_DESK(HotelAmenityGroup.SERVICES, false),

    FREE_PARKING(HotelAmenityGroup.TRANSPORT, true),
    PAID_PARKING(HotelAmenityGroup.TRANSPORT, false),
    AIRPORT_SHUTTLE(HotelAmenityGroup.TRANSPORT, true),
    SHUTTLE_SERVICE(HotelAmenityGroup.TRANSPORT, false),
    BICYCLE_RENTAL(HotelAmenityGroup.TRANSPORT, false),
    CAR_RENTAL(HotelAmenityGroup.TRANSPORT, false),

    FAMILY_ROOMS(HotelAmenityGroup.FAMILY, false),
    KIDS_CLUB(HotelAmenityGroup.FAMILY, false),
    BABYSITTING(HotelAmenityGroup.FAMILY, false),
    PLAYGROUND(HotelAmenityGroup.FAMILY, false),

    MEETING_ROOMS(HotelAmenityGroup.BUSINESS, false),
    BUSINESS_CENTER(HotelAmenityGroup.BUSINESS, false),

    SAFETY_DEPOSIT_BOX(HotelAmenityGroup.SAFETY, false),
    FIRE_EXTINGUISHER(HotelAmenityGroup.SAFETY, false),
    SMOKE_DETECTOR(HotelAmenityGroup.SAFETY, false),
    CCTV(HotelAmenityGroup.SAFETY, false),
    SECURITY_24H(HotelAmenityGroup.SAFETY, false),
    FIRST_AID_KIT(HotelAmenityGroup.SAFETY, false);

    private static final Map<String, HotelAmenity> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    private final HotelAmenityGroup group;
    private final boolean popular;

    HotelAmenity(HotelAmenityGroup group, boolean popular) {
        this.group = group;
        this.popular = popular;
    }

    public HotelAmenityGroup group() {
        return group;
    }

    public boolean popular() {
        return popular;
    }

    /** Known code for a stored value, tolerant of case and surrounding spaces. */
    public static Optional<HotelAmenity> fromValue(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_NAME.get(value.trim().toUpperCase(Locale.ROOT)));
    }
}
