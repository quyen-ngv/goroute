package com.ds.goroute.type;

public enum ModerationFlagStatus {
    PENDING,
    /**
     * A human looked and decided the content is fine. This is the false-positive signal
     * that drives MOD-08, not merely a way to close a row.
     */
    KEPT,
    REMOVED,
    ESCALATED
}
