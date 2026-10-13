package com.ds.goroute.type;

import java.util.Set;

/** A change to a trip's plan that the assistant may propose; a member applies it. */
public enum AssistantProposalAction {
    /** Add a catalogue place to a day, at a time when given. */
    ADD,
    /** Move an existing activity to another day (and time when given). */
    MOVE,
    /** Change the start/end time of an existing activity on its own day. */
    UPDATE_TIME,
    /** Remove an existing activity. */
    REMOVE,
    /** Put another catalogue place in an existing activity, keeping its day (and time unless given). */
    REPLACE,
    /** Lengthen the trip at its end so it lasts up to a given day; ADD/MOVE may target the new days. */
    ADD_DAYS,
    /** Add days before day 1 (arriving earlier); every activity keeps its date and gets a later number. */
    PREPEND_DAYS,
    /** Remove a range of days and their activities; the later days move up. */
    REMOVE_DAYS,
    /** Exchange the plans of two days. */
    SWAP_DAYS,
    /** Move the whole trip to other dates, same length and plan. */
    SHIFT_DATES;

    /** Changes that renumber or re-date the days: one per answer, never next to a day-numbered add or move. */
    public static final Set<AssistantProposalAction> RESHAPES_DAYS = Set.of(PREPEND_DAYS, REMOVE_DAYS, SWAP_DAYS, SHIFT_DATES);

    /** Changes whose day numbers assume the trip still starts on the date it started when proposed. */
    public static final Set<AssistantProposalAction> NUMBERS_DAYS =
            Set.of(ADD, MOVE, ADD_DAYS, PREPEND_DAYS, REMOVE_DAYS, SWAP_DAYS, SHIFT_DATES);

    /** Changes that name an existing activity, which must still exist when applied. */
    public static final Set<AssistantProposalAction> NAMES_ACTIVITY = Set.of(MOVE, UPDATE_TIME, REMOVE, REPLACE);
}
