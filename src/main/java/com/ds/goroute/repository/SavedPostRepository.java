package com.ds.goroute.repository;

import com.ds.goroute.type.SavedPostType;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of saved_posts for code that renders posts and must not depend on SavedPostService
 * (which itself reads posts to check what may be saved).
 */
public interface SavedPostRepository {

    /** Which of these posts the user saved; empty for a guest or an empty list. */
    Set<UUID> findSavedIds(UUID userId, SavedPostType postType, Collection<UUID> postIds);
}
