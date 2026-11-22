package com.ds.goroute.type;

/**
 * One person's place in a meetup (the host is not a participant row).
 *
 * <p>{@code REQUESTED → JOINED | DECLINED | WITHDRAWN}, {@code INVITED → JOINED | WITHDRAWN},
 * {@code JOINED → LEFT | REMOVED}; an OPEN meetup goes straight to {@code JOINED}. A DECLINED or
 * REMOVED person cannot ask again; WITHDRAWN and LEFT can. The host may invite anyone again,
 * whatever their row says, except while it is live.
 */
public enum MeetupParticipantStatus {
    REQUESTED,
    /** The host invited this person; accepting is a join that needs no approval. */
    INVITED,
    JOINED,
    DECLINED,
    WITHDRAWN,
    LEFT,
    REMOVED;

    /** Whether this person may send a new join request or join again. */
    public boolean canRejoin() {
        return this == WITHDRAWN || this == LEFT;
    }

    /** Whether the row is still live: a pending request, an invitation or a seat. */
    public boolean isActive() {
        return this == REQUESTED || this == INVITED || this == JOINED;
    }
}
