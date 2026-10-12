package com.ds.goroute.type;

/** A change to a trip's plan that the assistant may propose; a member applies it. */
public enum AssistantProposalAction {
    /** Add a catalogue place to a day, at a time when given. */
    ADD,
    /** Move an existing activity to another day (and time when given). */
    MOVE,
    /** Change the start/end time of an existing activity on its own day. */
    UPDATE_TIME,
    /** Remove an existing activity. */
    REMOVE
}
