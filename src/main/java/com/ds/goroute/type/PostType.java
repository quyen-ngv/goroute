package com.ds.goroute.type;

/** What a post is (posts.post_type, epic 20). */
public enum PostType {
    /** A free-standing article. */
    ARTICLE,
    /** A story of one trip, usually started from the draft POST /trips/{tripId}/post-draft writes. */
    TRIP_STORY
}
