package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateSavedCategoryRequest;
import com.ds.goroute.dto.response.SavedCategoryDetailResponse;
import com.ds.goroute.dto.response.SavedCategoryResponse;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.entity.SavedCategory;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedCategoryMapper;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.SavedCategoryService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedCategoryServiceImpl implements SavedCategoryService {

    /** Far more categories than anyone keeps; the list is not paged. */
    static final int MAX_LISTED_CATEGORIES = 500;
    /** Items one category shows, newest first. The map draws all of them, so the list is not paged. */
    static final int MAX_LISTED_ITEMS = 500;
    /** The image-cleanup entity that owns category covers. */
    static final String COVER_ENTITY = "SAVED_CATEGORY";
    /** Where {@code POST /v1/api/files/upload} stores a user's images. */
    private static final String USER_UPLOAD_PREFIX = "expenses/";

    private final SavedCategoryMapper categoryMapper;
    private final SavedPlaceMapper savedPlaceMapper;
    private final UserBlockService userBlockService;
    private final StorageService storageService;
    private final ImageStorageCleanupService imageStorageCleanupService;

    @Override
    @Transactional(readOnly = true)
    public List<SavedCategoryResponse> listMine(UUID userId) {
        return categoryMapper.findSummariesByUser(userId, false, MAX_LISTED_CATEGORIES).stream()
                .map(SavedCategoryServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedCategoryResponse> listPublic(UUID ownerId, UUID viewerId) {
        if (isBlocked(ownerId, viewerId)) {
            return List.of();
        }
        // An empty album on a profile only says "nothing here"; it stays visible to its owner.
        return categoryMapper.findSummariesByUser(ownerId, true, MAX_LISTED_CATEGORIES).stream()
                .filter(category -> itemCount(category) > 0)
                .map(SavedCategoryServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavedCategoryDetailResponse get(UUID viewerId, UUID categoryId) {
        SavedCategory category = categoryMapper.findSummaryById(categoryId);
        if (category == null) {
            throw notFound();
        }
        boolean isOwner = category.getUserId().equals(viewerId);
        // Private and blocked both answer 404: neither should confirm the category exists.
        if (!isOwner && (!category.getVisibility().isPublic() || isBlocked(category.getUserId(), viewerId))) {
            throw notFound();
        }
        List<SavedPlaceResponse> items = savedPlaceMapper
                .findByCategoryKey(category.getUserId(), category.getName(), MAX_LISTED_ITEMS).stream()
                .map(item -> toItemResponse(item, isOwner))
                .toList();
        return SavedCategoryDetailResponse.builder()
                .category(toResponse(category))
                .items(items)
                .owner(isOwner)
                .build();
    }

    @Override
    @Transactional
    public SavedCategoryResponse update(UUID userId, UUID categoryId, UpdateSavedCategoryRequest request) {
        SavedCategory category = requireOwned(userId, categoryId);
        String newName = request.getName().trim();
        String newCover = requireOwnUpload(userId, request.getCoverUrl());

        if (!category.getName().equals(newName)) {
            boolean isSameCategory = categoryKey(category.getName()).equals(categoryKey(newName));
            // Renaming onto another category would merge the two silently; that is not a rename.
            if (!isSameCategory && (categoryMapper.findByUserAndName(userId, newName) != null
                    || savedPlaceMapper.existsByCategoryKey(userId, newName))) {
                throw new BusinessException(ErrorConstant.SAVED_CATEGORY_NAME_TAKEN,
                        "You already have a category with this name", HttpStatus.CONFLICT);
            }
            savedPlaceMapper.deleteDuplicatesInCategory(userId, category.getName());
            int moved = savedPlaceMapper.renameCategory(userId, category.getName(), newName);
            log.info("Renamed saved category: userId={}, categoryId={}, items={}", userId, categoryId, moved);
        }

        if (!Objects.equals(category.getCoverUrl(), newCover)) {
            // Read while the row still names the old cover; the new one, if any, is kept.
            imageStorageCleanupService.deleteImagesForEntityRecord(
                    COVER_ENTITY, categoryId, newCover == null ? List.of() : List.of(newCover));
        }

        category.setName(newName);
        category.setCoverUrl(newCover);
        category.setVisibility(request.getVisibility());
        category.setUpdatedAt(LocalDateTime.now());
        if (categoryMapper.update(category) != 1) {
            throw notFound();
        }
        return toResponse(categoryMapper.findSummaryById(categoryId));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        SavedCategory category = requireOwned(userId, categoryId);
        imageStorageCleanupService.deleteImagesForEntityRecord(COVER_ENTITY, categoryId);
        int removedItems = savedPlaceMapper.deleteByCategoryKey(userId, category.getName());
        if (categoryMapper.deleteOwned(categoryId, userId) != 1) {
            throw notFound();
        }
        log.info("Deleted saved category: userId={}, categoryId={}, items={}", userId, categoryId, removedItems);
    }

    private SavedCategory requireOwned(UUID userId, UUID categoryId) {
        SavedCategory category = categoryMapper.findById(categoryId);
        if (category == null || !category.getUserId().equals(userId)) {
            throw notFound();
        }
        return category;
    }

    /**
     * A cover must be a photo this user uploaded. Anything else is refused: the cover is shown
     * on a public profile without passing image moderation, and replacing or deleting the
     * category deletes the cover's file, which must never be somebody else's.
     */
    private String requireOwnUpload(UUID userId, String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        String trimmed = coverUrl.trim();
        String key = storageService.extractObjectKey(trimmed);
        if (key == null || !key.startsWith(USER_UPLOAD_PREFIX + userId + "/")) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "The cover must be a photo you uploaded", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private boolean isBlocked(UUID ownerId, UUID viewerId) {
        return viewerId != null && !ownerId.equals(viewerId) && userBlockService.blockedBetween(ownerId, viewerId);
    }

    /** Matches the SQL side: LOWER(BTRIM(category)). */
    private static String categoryKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static int itemCount(SavedCategory category) {
        return category.getItemCount() == null ? 0 : category.getItemCount();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Saved category not found", HttpStatus.NOT_FOUND);
    }

    private static SavedCategoryResponse toResponse(SavedCategory category) {
        return SavedCategoryResponse.builder()
                .id(category.getId())
                .ownerId(category.getUserId())
                .name(category.getName())
                .coverUrl(category.getCoverUrl())
                .previewPhotoUrl(category.getLatestPhotoUrl())
                .visibility(category.getVisibility())
                .itemCount(itemCount(category))
                .lastSavedAt(category.getLastSavedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private static SavedPlaceResponse toItemResponse(SavedPlace item, boolean isOwner) {
        String itemType = item.getItemType() == null || item.getItemType().isBlank()
                ? "PLACE"
                : item.getItemType().trim().toUpperCase(Locale.ROOT);
        return SavedPlaceResponse.builder()
                .id(item.getId())
                .placeId(item.getPlaceId())
                .itemType(itemType)
                .name(item.getName())
                .address(item.getAddress())
                .lat(item.getLat())
                .lng(item.getLng())
                .category(item.getCategory())
                .rating(item.getRating())
                .photoUrl(item.getPhotoUrl())
                // Tags are the owner's own notes, not part of what a shared category shows.
                .tags(isOwner && item.getTags() != null ? List.of(item.getTags()) : null)
                .createdAt(item.getCreatedAt())
                .build();
    }
}
