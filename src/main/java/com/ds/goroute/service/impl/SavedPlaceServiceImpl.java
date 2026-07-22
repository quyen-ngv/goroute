package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.dto.response.SavedItemsOverviewResponse;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPlaceServiceImpl implements SavedPlaceService {

    private static final String DEFAULT_ITEM_TYPE = "PLACE";

    private final SavedPlaceMapper savedPlaceMapper;
    private final ImageStorageCleanupService imageStorageCleanupService;

    @Override
    public List<SavedPlaceResponse> getSavedPlaces(UUID userId, String category, String itemType, Integer page, Integer size) {
        int offset = page * size;
        String normalizedItemType = itemType == null || itemType.trim().isEmpty()
                ? null
                : normalizeItemType(itemType);
        List<SavedPlace> places = savedPlaceMapper.findByUserId(userId, category, normalizedItemType, size, offset);
        
        return places.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest request) {
        String itemType = normalizeItemType(request.getItemType());
        SavedPlace existing = savedPlaceMapper.findByUserIdAndPlaceId(userId, request.getPlaceId(), itemType);
        if (existing != null) {
            savedPlaceMapper.updateCategory(existing.getId(), request.getCategory());
            return toResponse(savedPlaceMapper.findById(existing.getId()));
        }

        SavedPlace savedPlace = SavedPlace.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(request.getPlaceId())
                .itemType(itemType)
                .name(request.getName())
                .address(request.getAddress())
                .lat(request.getLat())
                .lng(request.getLng())
                .category(request.getCategory())
                .rating(request.getRating())
                .photoUrl(request.getPhotoUrl())
                .tags(request.getTags() != null ? request.getTags().toArray(new String[0]) : null)
                .createdAt(LocalDateTime.now())
                .build();

        savedPlaceMapper.insert(savedPlace);
        log.info("Saved item: userId={}, itemType={}, placeId={}", userId, itemType, request.getPlaceId());
        
        return toResponse(savedPlace);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedItemsOverviewResponse getSavedItemsOverview(UUID userId) {
        List<SavedPlaceResponse> savedItems = savedPlaceMapper.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return SavedItemsOverviewResponse.builder()
                .savedItems(savedItems)
                .tripItems(savedPlaceMapper.findTripItemsByUserId(userId))
                .build();
    }

    @Override
    @Transactional
    public void unsavePlace(UUID userId, UUID savedPlaceId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        imageStorageCleanupService.deleteImagesForEntityRecord("SAVED_PLACE", savedPlaceId);
        savedPlaceMapper.deleteById(savedPlaceId);
        log.info("Unsaved item: userId={}, id={}, itemType={}", userId, savedPlaceId, savedPlace.getItemType());
    }

    @Override
    @Transactional
    public SavedPlaceResponse updateTags(UUID userId, UUID savedPlaceId, List<String> tags) {
        requireOwnedSavedPlace(userId, savedPlaceId);
        savedPlaceMapper.updateTags(savedPlaceId, tags.toArray(new String[0]));
        SavedPlace updated = savedPlaceMapper.findById(savedPlaceId);
        return toResponse(updated);
    }

    private SavedPlace requireOwnedSavedPlace(UUID userId, UUID savedPlaceId) {
        SavedPlace savedPlace = savedPlaceMapper.findById(savedPlaceId);
        if (savedPlace == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!userId.equals(savedPlace.getUserId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, HttpStatus.FORBIDDEN);
        }
        return savedPlace;
    }

    private String normalizeItemType(String itemType) {
        if (itemType == null || itemType.trim().isEmpty()) {
            return DEFAULT_ITEM_TYPE;
        }
        return itemType.trim().toUpperCase();
    }

    private SavedPlaceResponse toResponse(SavedPlace place) {
        return SavedPlaceResponse.builder()
                .id(place.getId())
                .placeId(place.getPlaceId())
                .itemType(normalizeItemType(place.getItemType()))
                .name(place.getName())
                .address(place.getAddress())
                .lat(place.getLat())
                .lng(place.getLng())
                .category(place.getCategory())
                .rating(place.getRating())
                .photoUrl(place.getPhotoUrl())
                .tags(place.getTags() != null ? List.of(place.getTags()) : null)
                .createdAt(place.getCreatedAt())
                .build();
    }
}
