package com.ds.goroute.type;

/**
 * The Mapbox price lists the server spends from, each with its own monthly cap. The name is
 * what {@code map_provider_usage.api} stores.
 */
public enum MapBillingApi {
    SEARCH(BusinessConfigKey.MAP_BUDGET_SEARCH_MONTHLY_CAP),
    GEOCODE(BusinessConfigKey.MAP_BUDGET_GEOCODE_MONTHLY_CAP),
    DIRECTIONS(BusinessConfigKey.MAP_BUDGET_DIRECTIONS_MONTHLY_CAP);

    private final BusinessConfigKey monthlyCapKey;

    MapBillingApi(BusinessConfigKey monthlyCapKey) {
        this.monthlyCapKey = monthlyCapKey;
    }

    public BusinessConfigKey monthlyCapKey() {
        return monthlyCapKey;
    }
}
