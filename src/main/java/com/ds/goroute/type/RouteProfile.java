package com.ds.goroute.type;

/**
 * How one leg of a day is travelled, as far as road routing cares. The app maps its transport
 * modes onto these: walking; motorbike, car, taxi and bus on roads; and modes with no road route
 * (flight, ferry, boat, train) as a straight line with no travel time.
 */
public enum RouteProfile {
    WALKING,
    DRIVING,
    STRAIGHT
}
