package com.ds.goroute.type;

/**
 * One piece of a day's line: walked or ridden, a signal gap drawn dashed (TR-11), or a
 * flight drawn as an arc and counted apart (TR-12).
 */
public enum TrackSegmentKind {
    TRACK,
    GAP,
    FLIGHT
}
