package com.ds.goroute.service;

import com.ds.goroute.dto.request.FeedFeedbackRequest;
import com.ds.goroute.dto.response.FeedFeedbackResponse;
import com.ds.goroute.type.FeedItemType;

import java.util.Set;
import java.util.UUID;

/**
 * "Hide this post" and "hide posts from this person" (feed_feedback, V245).
 *
 * <p>The SQL of the feed endpoints filters on the table directly. {@link #hiddenFor} is for
 * lists that are not filtered in SQL, such as the shared public trip ranking window.
 */
public interface FeedFeedbackService {

    /** Idempotent: hiding what is already hidden returns the existing hide. */
    FeedFeedbackResponse hide(UUID userId, FeedFeedbackRequest request);

    /** Undo. A no-op when the hide is already undone, expired or someone else's. */
    void unhide(UUID userId, UUID feedbackId);

    /** The viewer's hides in force now. {@link Hidden#NONE} for a guest. */
    Hidden hiddenFor(UUID viewerId);

    /** Removes undone and expired hides older than 90 days; returns how many went. */
    int purgeFinished();

    /** One viewer's hides, read once and asked per row. */
    record Hidden(Set<String> posts, Set<UUID> authors) {

        public static final Hidden NONE = new Hidden(Set.of(), Set.of());

        public boolean hides(FeedItemType type, UUID itemId, UUID authorId) {
            return (authorId != null && authors.contains(authorId))
                    || (itemId != null && posts.contains(key(type, itemId)));
        }

        public static String key(FeedItemType type, UUID itemId) {
            return type.name() + ':' + itemId;
        }
    }
}
