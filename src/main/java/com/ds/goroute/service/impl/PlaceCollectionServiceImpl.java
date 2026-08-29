package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AddCollectionItemRequest;
import com.ds.goroute.dto.request.UpsertPlaceCollectionRequest;
import com.ds.goroute.dto.response.PlaceCollectionItemResponse;
import com.ds.goroute.dto.response.PlaceCollectionResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceCollection;
import com.ds.goroute.entity.PlaceCollectionItem;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceCollectionMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.PlaceCollectionService;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.PlaceVisibilityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceCollectionServiceImpl implements PlaceCollectionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_ITEMS = 100;
    private static final int SLUG_BYTES = 12;

    private final PlaceCollectionMapper collectionMapper;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public PlaceCollectionResponse create(UUID ownerId, UpsertPlaceCollectionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ContentVisibility visibility = request.getVisibility() == null
                ? ContentVisibility.PRIVATE
                : request.getVisibility();

        PlaceCollection collection = PlaceCollection.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .visibility(visibility)
                .shareSlug(visibility.isPublic() ? newSlug() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        collectionMapper.insert(collection);
        return toResponse(collection, List.of(), ownerId);
    }

    @Override
    @Transactional
    public PlaceCollectionResponse update(UUID ownerId, UUID collectionId,
                                          UpsertPlaceCollectionRequest request) {
        PlaceCollection collection = requireOwned(ownerId, collectionId);
        ContentVisibility visibility = request.getVisibility() == null
                ? collection.getVisibility()
                : request.getVisibility();

        collection.setName(request.getName().trim());
        collection.setDescription(request.getDescription());
        collection.setCoverImageUrl(request.getCoverImageUrl());
        collection.setVisibility(visibility);
        // Un-publishing drops the slug, so a link somebody already has stops resolving.
        // Hiding it from a list while the direct address still works is not un-publishing.
        collection.setShareSlug(visibility.isPublic()
                ? (collection.getShareSlug() == null ? newSlug() : collection.getShareSlug())
                : null);
        collection.setUpdatedAt(LocalDateTime.now());

        if (collectionMapper.update(collection) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
        return get(ownerId, collectionId);
    }

    @Override
    @Transactional
    public void delete(UUID ownerId, UUID collectionId) {
        if (collectionMapper.markRemoved(collectionId, ownerId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceCollectionResponse get(UUID viewerId, UUID collectionId) {
        PlaceCollection collection = require(collectionId);
        if (!collection.getOwnerId().equals(viewerId) && !collection.getVisibility().isPublic()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
        return toResponse(collection, collectionMapper.findItems(collectionId), viewerId);
    }

    @Override
    @Transactional
    public PlaceCollectionResponse getByShareSlug(String shareSlug) {
        PlaceCollection collection = collectionMapper.findPublicBySlug(shareSlug);
        if (collection == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
        collectionMapper.incrementViewCount(collection.getId());
        return toResponse(collection, collectionMapper.findItems(collection.getId()), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceCollectionResponse> listMine(UUID ownerId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return collectionMapper.findByOwner(ownerId, safeSize, Math.max(0, page) * safeSize).stream()
                .map(collection -> toResponse(collection, List.of(), ownerId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countMine(UUID ownerId) {
        return collectionMapper.countByOwner(ownerId);
    }

    @Override
    @Transactional
    public PlaceCollectionResponse addItem(UUID ownerId, UUID collectionId, AddCollectionItemRequest request) {
        requireOwned(ownerId, collectionId);
        int existing = collectionMapper.countItems(collectionId);
        if (existing >= MAX_ITEMS) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A collection can hold at most " + MAX_ITEMS + " places");
        }

        collectionMapper.insertItem(PlaceCollectionItem.builder()
                .id(UUID.randomUUID())
                .collectionId(collectionId)
                .placeId(request.getPlaceId())
                .displayName(request.getDisplayName().trim())
                .displayNote(request.getDisplayNote())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .position(existing)
                .createdAt(LocalDateTime.now())
                .build());
        collectionMapper.refreshItemCount(collectionId);
        return get(ownerId, collectionId);
    }

    @Override
    @Transactional
    public PlaceCollectionResponse removeItem(UUID ownerId, UUID collectionId, UUID itemId) {
        requireOwned(ownerId, collectionId);
        collectionMapper.deleteItem(collectionId, itemId);
        collectionMapper.refreshItemCount(collectionId);
        return get(ownerId, collectionId);
    }

    @Override
    @Transactional
    public PlaceCollectionResponse reorder(UUID ownerId, UUID collectionId, List<UUID> orderedItemIds) {
        requireOwned(ownerId, collectionId);
        int position = 0;
        for (UUID itemId : orderedItemIds) {
            collectionMapper.updateItemPosition(collectionId, itemId, position++);
        }
        return get(ownerId, collectionId);
    }

    // --- helpers ---------------------------------------------------------------------

    private PlaceCollection require(UUID collectionId) {
        PlaceCollection collection = collectionMapper.findById(collectionId);
        if (collection == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
        return collection;
    }

    private PlaceCollection requireOwned(UUID ownerId, UUID collectionId) {
        PlaceCollection collection = require(collectionId);
        if (!collection.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "You can only change your own collections");
        }
        return collection;
    }

    /** Long enough that a share link cannot be found by trying. */
    private String newSlug() {
        byte[] bytes = new byte[SLUG_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PlaceCollectionResponse toResponse(PlaceCollection collection,
                                               List<PlaceCollectionItem> items,
                                               UUID viewerId) {
        User owner = userRepository.findById(collection.getOwnerId()).orElse(null);
        Map<UUID, Place> places = loadPlaces(items);

        return PlaceCollectionResponse.builder()
                .id(collection.getId())
                .ownerId(collection.getOwnerId())
                .ownerDisplayName(owner == null ? null : owner.getFullName())
                .name(collection.getName())
                .description(collection.getDescription())
                .coverImageUrl(collection.getCoverImageUrl())
                .visibility(collection.getVisibility())
                // The share link is the owner's to hand out, so only they receive it.
                .shareSlug(collection.getOwnerId().equals(viewerId) ? collection.getShareSlug() : null)
                .itemCount(collection.getItemCount() == null ? items.size() : collection.getItemCount())
                .viewCount(collection.getViewCount() == null ? 0 : collection.getViewCount())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .items(items.stream().map(item -> toItemResponse(item, places.get(item.getPlaceId()))).toList())
                .build();
    }

    private Map<UUID, Place> loadPlaces(List<PlaceCollectionItem> items) {
        List<UUID> placeIds = items.stream()
                .map(PlaceCollectionItem::getPlaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findByIds(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (first, second) -> first));
    }

    private PlaceCollectionItemResponse toItemResponse(PlaceCollectionItem item, Place place) {
        boolean available = place != null && place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE;
        return PlaceCollectionItemResponse.builder()
                .id(item.getId())
                .placeId(item.getPlaceId())
                // The stored display name is used, so an entry still reads correctly when
                // the catalogue row behind it is gone.
                .displayName(item.getDisplayName())
                .displayNote(item.getDisplayNote())
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .position(item.getPosition() == null ? 0 : item.getPosition())
                .thumbnail(place == null ? null : place.getThumbnail())
                .category(place == null ? null : place.getCategory())
                .rating(place == null ? null : place.getReviewRating())
                .placeAvailable(available)
                .build();
    }
}
