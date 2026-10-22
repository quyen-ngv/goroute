package com.ds.goroute.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the Feed search looks for (epic 12 §4.3). {@link #ALL} is a request filter only: it asks
 * for check-ins and trips mixed by relevance, plus a short list of people; every result carries
 * one of the other three.
 */
public enum SocialSearchType {
    ALL,
    USER,
    CHECKIN,
    TRIP;

    /** The kinds of document a page of this filter lists. */
    public Set<SocialSearchType> listedKinds() {
        return switch (this) {
            case ALL -> EnumSet.of(CHECKIN, TRIP);
            case USER -> EnumSet.of(USER);
            case CHECKIN -> EnumSet.of(CHECKIN);
            case TRIP -> EnumSet.of(TRIP);
        };
    }
}
