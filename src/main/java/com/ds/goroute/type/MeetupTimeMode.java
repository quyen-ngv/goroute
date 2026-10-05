package com.ds.goroute.type;

/**
 * When a meetup happens on its day: any time until local midnight ({@code FLEXIBLE}), or at a
 * given local time ({@code EXACT}).
 */
public enum MeetupTimeMode {
    FLEXIBLE,
    EXACT
}
