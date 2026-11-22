package com.ds.goroute.type;

/**
 * The map calls the app makes through the server, each with its own per-minute and per-day
 * limit per user. Cached answers count too: the limit protects the server as well as the bill.
 * The name is what {@code map_search_usage_daily.operation} stores.
 */
public enum MapSearchOperation {
    AUTOCOMPLETE(BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_MINUTE,
            BusinessConfigKey.MAP_SEARCH_AUTOCOMPLETE_PER_DAY),
    PLACE_DETAIL(BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_MINUTE,
            BusinessConfigKey.MAP_SEARCH_PLACE_DETAIL_PER_DAY),
    GEOCODE(BusinessConfigKey.MAP_SEARCH_GEOCODE_PER_MINUTE,
            BusinessConfigKey.MAP_SEARCH_GEOCODE_PER_DAY),
    ROUTE(BusinessConfigKey.MAP_SEARCH_ROUTE_PER_MINUTE,
            BusinessConfigKey.MAP_SEARCH_ROUTE_PER_DAY);

    private final BusinessConfigKey perMinuteKey;
    private final BusinessConfigKey perDayKey;

    MapSearchOperation(BusinessConfigKey perMinuteKey, BusinessConfigKey perDayKey) {
        this.perMinuteKey = perMinuteKey;
        this.perDayKey = perDayKey;
    }

    public BusinessConfigKey perMinuteKey() {
        return perMinuteKey;
    }

    public BusinessConfigKey perDayKey() {
        return perDayKey;
    }
}
