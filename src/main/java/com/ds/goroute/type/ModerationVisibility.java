package com.ds.goroute.type;

/**
 * How many people can see a piece of content. Strictness is proportional to this
 * (MOD-07): moderation exists to protect readers, so content with no readers is not
 * filtered.
 */
public enum ModerationVisibility {
    /** Reviews, city stories, public trips, check-ins, place names. */
    PUBLIC,
    /** Trip notes, activity comments, expenses - visible to trip members. */
    GROUP,
    /** One-to-one chat. */
    DIRECT,
    /** Personal notes and drafts. */
    PRIVATE
}
