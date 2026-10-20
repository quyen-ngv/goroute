package com.ds.goroute.type;

/** A post a follower can be told about. */
public enum FollowingPostType {
    CHECKIN(NotificationType.FOLLOWING_CHECKIN),
    TRIP(NotificationType.FOLLOWING_TRIP_SHARED);

    private final NotificationType notificationType;

    FollowingPostType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public NotificationType notificationType() {
        return notificationType;
    }

    /** {@code notifications.dedupe_key} of this post: one row per recipient per post. */
    public String dedupeKey(java.util.UUID postId) {
        return "FOLLOWING:" + name() + ":" + postId;
    }
}
