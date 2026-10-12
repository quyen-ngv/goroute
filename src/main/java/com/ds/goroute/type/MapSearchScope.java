package com.ds.goroute.type;

/** What a map autocomplete looks for; sent by the app as {@code scope} on {@code /v1/api/goong/autocomplete}. */
public enum MapSearchScope {
    /** Any place: venues, streets, addresses, areas. */
    ALL,
    /** Areas a trip goes to: countries, states/provinces, districts, cities. Mapbox only; Goong has no filter. */
    AREA
}
