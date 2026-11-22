package com.ds.goroute.event;

import java.util.UUID;

/**
 * Published when a post is published for the first time (posts.published_at went from NULL to a
 * time). An unpublish and a later publish never raise it again, so followers hear of a post once.
 *
 * @param authorId the post's author (for a managed account, the account, not the admin)
 */
public record PostPublishedEvent(UUID postId, UUID authorId) {
}
