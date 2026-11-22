package com.ds.goroute.type;

/** What a viewer asked the Feed not to show them (feed_feedback.kind, V245). */
public enum FeedFeedbackKind {
    /** One post, for good. Needs an item type and id. */
    HIDE_POST,
    /** Every post by one person, for FEED.HIDE_AUTHOR_DAYS. Needs a target user. */
    HIDE_AUTHOR
}
