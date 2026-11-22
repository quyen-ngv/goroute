package com.ds.goroute.type;

/** What a track deletion removes: time ranges within one day, a whole day, or the whole trip. */
public enum TrackDeletionScope {
    RANGES,
    DAY,
    TRIP
}
