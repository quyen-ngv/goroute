package com.ds.goroute.type;

/**
 * How the location on a check-in was arrived at (CHK-03).
 *
 * <p>Recorded because it is the difference between "the user picked a real place" and
 * "the system named a coordinate", and the verification and reward rules need to tell
 * those apart.
 */
public enum CheckinLocationSource {
    /** Picked from the curated catalogue. */
    CATALOGUE_PLACE,
    /** Picked from the activities of the trip in progress. */
    TRIP_ACTIVITY,
    /** Picked from map search; not in the catalogue. */
    MAP_SEARCH,
    /** Named by reverse-geocoding the coordinates into ward/district/province. */
    REVERSE_GEOCODE,
    /** The user typed their own name for the spot. */
    USER_NAMED,
    /** Nothing could be resolved -- offline or the lookup failed. The check-in is still saved. */
    COORDINATES_ONLY
}
