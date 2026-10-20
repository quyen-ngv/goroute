package com.ds.goroute.type;

/** {@code follower_fanout_logs.status}. */
public enum FollowerFanoutStatus {
    RUNNING,
    DONE,
    FAILED,
    /** The post stopped being public or was taken down while its followers were being told. */
    CANCELLED,
    /** Nothing to do: the feature is off for everyone the post could reach, or the trip was announced before. */
    SKIPPED
}
