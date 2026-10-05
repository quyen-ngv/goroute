package com.ds.goroute.type;

/**
 * One person's place in a meetup (the host is not a participant row).
 *
 * <p>{@code REQUESTED → JOINED | DECLINED | WITHDRAWN}, {@code JOINED → LEFT | REMOVED}; an OPEN
 * meetup goes straight to {@code JOINED}. A DECLINED or REMOVED person cannot ask again;
 * WITHDRAWN and LEFT can.
 */
public enum MeetupParticipantStatus {
    REQUESTED,
    JOINED,
    DECLINED,
    WITHDRAWN,
    LEFT,
    REMOVED;

    /** Whether this person may send a new join request or join again. */
    public boolean canRejoin() {
        return this == WITHDRAWN || this == LEFT;
    }

    /** Whether the row is still live: a pending request or a seat. */
    public boolean isActive() {
        return this == REQUESTED || this == JOINED;
    }
}
