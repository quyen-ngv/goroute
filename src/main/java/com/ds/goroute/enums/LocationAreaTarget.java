package com.ds.goroute.enums;

/**
 * Tables that carry a primary tourist area ({@code location_image_id}).
 *
 * <p>The enum is the whitelist the admin auto-map job dispatches on: the mapper selects
 * SQL per constant through {@code <choose>} rather than interpolating a table name, so a
 * request can never name a table that is not listed here.
 */
public enum LocationAreaTarget {

    /** Catalogue places. Own coordinates, plus address and destination text. */
    PLACE("Địa điểm"),

    /** Trips. The destination coordinate the owner picked, else the destination text. */
    TRIP("Chuyến đi"),

    /** Per-destination legs of a trip. */
    TRIP_DESTINATION("Điểm đến trong chuyến đi"),

    /** Food popularity per city. No coordinates; keyed by the shared city slug vocabulary. */
    FOOD_CITY_SCORE("Món ăn theo thành phố"),

    /** Tours and partner activity products - both live in {@code activity_bookings}. */
    ACTIVITY("Tour / hoạt động"),

    /** Hotels. No coordinates of their own; they inherit the area of their Place. */
    HOTEL("Khách sạn / lưu trú");

    private final String label;

    LocationAreaTarget(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Hotels inherit from places, so they must be resolved after places in one run. */
    public boolean inheritsFromPlace() {
        return this == HOTEL;
    }

    /** Whether a name-based fallback makes sense when coordinates resolve nothing. */
    public boolean supportsNameFallback() {
        return this != HOTEL;
    }
}
