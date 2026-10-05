package com.ds.goroute.type;

/**
 * What kind of meetup a host proposes (SPEC_V2 §0.7). The emoji and the Vietnamese label are
 * presentation and live in the app and the console; rideshare is deliberately absent for
 * safety reasons.
 */
public enum MeetupCategory {
    FOOD_DRINK,
    COFFEE,
    NIGHTLIFE,
    OUTDOOR_ACTIVE,
    SIGHTSEEING,
    ENTERTAINMENT,
    SHOPPING,
    WELLNESS,
    SOCIAL,
    OTHER
}
