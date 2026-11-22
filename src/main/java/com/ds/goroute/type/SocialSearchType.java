package com.ds.goroute.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the Feed search looks for (epic 12 §4.4). {@link #ALL} is a request filter only: it asks
 * for check-ins and trips mixed by relevance, plus a short row of every other kind; every result
 * carries one of the other values.
 *
 * <p>People, check-ins, trips, tours and events live in the Feed search index; places and stays
 * are catalogue places and come from the place index.
 */
public enum SocialSearchType {
    ALL,
    USER,
    CHECKIN,
    TRIP,
    /** Catalogue places to eat, drink and see (groups FOOD_AND_DRINK, ATTRACTIONS). */
    PLACE,
    /** Catalogue places to stay (group ACCOMMODATION), with the bookable hotel when there is one. */
    STAY,
    /** Tours and tickets: partner products for marketplace beta users, the imported catalogue otherwise. */
    TOUR,
    EVENT;

    /** The kinds of Feed-search-index document a page of this filter lists; none for places and stays. */
    public Set<SocialSearchType> listedKinds() {
        return switch (this) {
            case ALL -> EnumSet.of(CHECKIN, TRIP);
            case USER, CHECKIN, TRIP, TOUR, EVENT -> EnumSet.of(this);
            case PLACE, STAY -> EnumSet.noneOf(SocialSearchType.class);
        };
    }

    /** Whether results of this kind come from the place index rather than the Feed search index. */
    public boolean isCataloguePlace() {
        return this == PLACE || this == STAY;
    }
}
