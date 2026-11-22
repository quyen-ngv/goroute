package com.ds.goroute.type;

/**
 * How precisely the phone records a route. Each mode has its own distance filter and
 * accuracy cut-off (TRACKER config keys).
 */
public enum TrackAccuracyMode {
    HIGH,
    BALANCED,
    SAVER
}
