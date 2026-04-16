package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final SavedPlaceMapper savedPlaceMapper;

    @Override
    public List<SavedPlaceResponse> getSavedPlaces(UUID userId, String category, Integer page, Integer size) {
        int offset = page * size;
        List<SavedPlace> places = savedPlaceMapper.findByUserId(userId, category, size, offset);
        
        return places.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest request) {
        // Check if already saved
        SavedPlace existing = savedPlaceMapper.findByUserIdAndPlaceId(userId, request.getPlaceId());
        if (existing != null) {
            throw new RuntimeException("Place already saved");
        }

        SavedPlace savedPlace = SavedPlace.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(request.getPlaceId())
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
        log.info("Saved place: userId={}, placeId={}", userId, request.getPlaceId());
        
        return toResponse(savedPlace);
    }

    @Override
    @Transactional
    public void unsavePlace(UUID savedPlaceId) {
        savedPlaceMapper.deleteById(savedPlaceId);
        log.info("Unsaved place: id={}", savedPlaceId);
    }

    @Override
    @Transactional
    public SavedPlaceResponse updateTags(UUID savedPlaceId, List<String> tags) {
        savedPlaceMapper.updateTags(savedPlaceId, tags.toArray(new String[0]));
        SavedPlace updated = savedPlaceMapper.findById(savedPlaceId);
        return toResponse(updated);
    }

    private SavedPlaceResponse toResponse(SavedPlace place) {
        return SavedPlaceResponse.builder()
                .id(place.getId())
                .placeId(place.getPlaceId())
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
