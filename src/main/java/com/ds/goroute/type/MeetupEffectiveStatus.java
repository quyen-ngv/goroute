package com.ds.goroute.type;

/**
 * The status a client shows. It is the stored {@link MeetupStatus}, except that time is folded
 * in: a review still pending when the meetup's window has passed reads {@code EXPIRED}, and an
 * approved meetup whose window has passed reads {@code ENDED}. Neither is ever stored.
 */
public enum MeetupEffectiveStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    CANCELLED,
    REMOVED,
    EXPIRED,
    ENDED;

    public static MeetupEffectiveStatus of(MeetupStatus status, boolean past) {
        if (past && status == MeetupStatus.PENDING_REVIEW) {
            return EXPIRED;
        }
        if (past && status == MeetupStatus.APPROVED) {
            return ENDED;
        }
        return valueOf(status.name());
    }
}
