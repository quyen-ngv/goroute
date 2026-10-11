package com.ds.goroute.type;

/** How {@code GET /v1/api/places/search} orders the places it finds. */
public enum PlaceSearchSort {
    /** Name match and nearness to the point (the default). */
    RELEVANCE,
    /** Trust-adjusted rating, highest first. */
    RATING,
    /**
     * How much travellers use the place: trips and check-ins (V225), the same score the map
     * tiles keep places by ({@code PlaceProminence}). Google's stars do not count.
     */
    POPULARITY
}
