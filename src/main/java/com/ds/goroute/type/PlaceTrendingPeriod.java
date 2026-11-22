package com.ds.goroute.type;

/**
 * The window {@code GET /v1/api/places/trending} counts trips and check-ins over, rolling back
 * from now (not a calendar week, month or year, which would start nearly empty).
 */
public enum PlaceTrendingPeriod {
    WEEK(7),
    MONTH(30),
    YEAR(365);

    private final int days;

    PlaceTrendingPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
