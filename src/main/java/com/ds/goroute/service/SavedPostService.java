package com.ds.goroute.service;

import com.ds.goroute.dto.response.SavedPostPageResponse;
import com.ds.goroute.type.SavedPostType;

import java.util.UUID;

/**
 * Other people's check-ins and reviews a user saved to read later (epic 06, §2.6.2).
 *
 * <p>Saves are private and never notify the author. Only a post the user could open right now
 * can be saved, and never the user's own.
 */
public interface SavedPostService {

    /** Idempotent. 404 when the post cannot be seen, 400 for the user's own post. */
    void save(UUID userId, SavedPostType postType, UUID postId);

    /** Idempotent; also removes saves whose post no longer exists. */
    void unsave(UUID userId, SavedPostType postType, UUID postId);

    /**
     * One page of the user's saves, newest saved first unless {@code oldestFirst}.
     * {@code cursor} is the previous page's {@code nextCursor}; null for the first page.
     */
    SavedPostPageResponse list(UUID userId, boolean oldestFirst, String cursor, int size);
}
