package com.ds.goroute.type;

/**
 * The kinds of post the Feed shows (feed_impressions.item_type, feed_feedback.item_type, V245).
 * The id of a CHECKIN is user_checkins.id, of a REVIEW user_reviews.id, of a TRIP trips.id.
 */
public enum FeedItemType {
    CHECKIN,
    REVIEW,
    TRIP
}
