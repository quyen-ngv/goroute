package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpdateSavedCategoryRequest;
import com.ds.goroute.dto.response.SavedCategoryDetailResponse;
import com.ds.goroute.dto.response.SavedCategoryResponse;

import java.util.List;
import java.util.UUID;

/** The user's saved categories: rename, cover, visibility, delete, and showing them on a profile. */
public interface SavedCategoryService {

    /** Every category of the user, most recently saved into first. */
    List<SavedCategoryResponse> listMine(UUID userId);

    /**
     * The owner's PUBLIC categories that hold at least one item, as {@code viewerId} sees them on
     * the owner's profile. Empty when either has blocked the other.
     */
    List<SavedCategoryResponse> listPublic(UUID ownerId, UUID viewerId);

    /** One category and its items, for its owner or, when it is PUBLIC, for anyone not blocked. */
    SavedCategoryDetailResponse get(UUID viewerId, UUID categoryId);

    /** Renames (moving every item along), sets the cover and the visibility. Owner only. */
    SavedCategoryResponse update(UUID userId, UUID categoryId, UpdateSavedCategoryRequest request);

    /** Deletes the category and every save in it. A place also saved elsewhere stays saved there. */
    void delete(UUID userId, UUID categoryId);
}
