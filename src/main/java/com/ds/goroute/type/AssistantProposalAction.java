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
    SHIFT_DATES,
    /** Change the trip's own fields: name, description, budget, currency, visibility, starting point. */
    EDIT_TRIP,
    /** Change an existing activity's fields: name, notes, cost, address, category, transport, times. */
    EDIT_ACTIVITY,
    /** Add an activity that is not a catalogue place (a flight, a bus, a booking read from a photo). */
    NEW_ACTIVITY,
    /** Log an expense, split equally between the members it names (or everyone). */
    ADD_EXPENSE,
    /** Change an existing expense's amount, category, description, date or payer. */
    EDIT_EXPENSE,
    /** Delete an existing expense. */
    REMOVE_EXPENSE,
    /** Save a shared note on the trip, or on one of its activities. */
    ADD_NOTE,
    /** Add a person without an account to the trip as a guest, so expenses can name them. */
    ADD_GUEST;

    /** Changes that renumber or re-date the days: one per answer, never next to a day-numbered add or move. */
    public static final Set<AssistantProposalAction> RESHAPES_DAYS = Set.of(PREPEND_DAYS, REMOVE_DAYS, SWAP_DAYS, SHIFT_DATES);

    /** Changes whose day numbers assume the trip still starts on the date it started when proposed. */
    public static final Set<AssistantProposalAction> NUMBERS_DAYS =
            Set.of(ADD, MOVE, ADD_DAYS, PREPEND_DAYS, REMOVE_DAYS, SWAP_DAYS, SHIFT_DATES, NEW_ACTIVITY);

    /** Changes that name an existing activity, which must still exist when applied. */
    public static final Set<AssistantProposalAction> NAMES_ACTIVITY = Set.of(MOVE, UPDATE_TIME, REMOVE, REPLACE, EDIT_ACTIVITY);

    /** Changes that name an existing expense, which must still exist when applied. */
    public static final Set<AssistantProposalAction> NAMES_EXPENSE = Set.of(EDIT_EXPENSE, REMOVE_EXPENSE);
}
