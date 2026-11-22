package com.ds.goroute.type;

/**
 * Where a post is in its life (posts.status, epic 20). A removed post is not a status: it is
 * {@code is_removed}, the same soft delete as a check-in.
 */
public enum PostStatus {
    /** Only its author (and, for a managed account, the console) can read it. */
    DRAFT,
    /** Readable by everyone when also PUBLIC. Unpublishing goes back to DRAFT and keeps published_at. */
    PUBLISHED
}
