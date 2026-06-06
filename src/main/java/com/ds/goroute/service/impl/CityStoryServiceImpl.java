package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateCityStoryRequest;
import com.ds.goroute.dto.response.CityStoryFeedResponse;
import com.ds.goroute.dto.response.CityStoryGroupResponse;
import com.ds.goroute.dto.response.CityStoryItemResponse;
import com.ds.goroute.dto.response.CityStoryLikeResponse;
import com.ds.goroute.dto.response.CityStoryPlaceSummary;
import com.ds.goroute.entity.CityStory;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.CityStoryMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.CityStoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CityStoryServiceImpl implements CityStoryService {

    private static final int STORY_TTL_HOURS = 24;

    private final CityStoryMapper cityStoryMapper;
    private final LocationImageRepository locationImageRepository;
    private final PlaceRepository placeRepository;

    @Override
    @Transactional(readOnly = true)
    public CityStoryFeedResponse getFeed(UUID userId) {
        LocalDateTime since = sinceWindow();
        List<CityStory> activeStories = cityStoryMapper.selectActiveSince(since);
        if (activeStories.isEmpty()) {
            return CityStoryFeedResponse.builder().cities(List.of()).build();
        }

        Set<UUID> viewedIds = userId != null
                ? new HashSet<>(cityStoryMapper.selectViewedStoryIdsSince(userId, since))
                : Set.of();
        Set<UUID> likedIds = userId != null
                ? new HashSet<>(cityStoryMapper.selectLikedStoryIdsSince(userId, since))
                : Set.of();

        Map<UUID, LocationImage> locationsById = locationImageRepository.findAll().stream()
                .collect(Collectors.toMap(LocationImage::getId, loc -> loc, (a, b) -> a));

        Map<UUID, List<CityStory>> grouped = activeStories.stream()
                .collect(Collectors.groupingBy(CityStory::getLocationImageId));

        List<CityStoryGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<CityStory>> entry : grouped.entrySet()) {
            LocationImage location = locationsById.get(entry.getKey());
            if (location == null) {
                continue;
            }

            List<CityStory> stories = entry.getValue().stream()
                    .sorted(Comparator.comparing(CityStory::getCreatedAt))
                    .toList();

            List<CityStoryItemResponse> storyResponses = stories.stream()
                    .map(story -> mapStory(story, viewedIds, likedIds))
                    .toList();

            boolean hasUnviewed = userId == null
                    || storyResponses.stream().anyMatch(item -> !item.isHasViewed());

            groups.add(CityStoryGroupResponse.builder()
                    .locationId(location.getId())
                    .fullAddress(location.getFullAddress())
                    .citySlug(location.getCitySlug())
                    .avatarUrl(resolveAvatarUrl(location))
                    .hasUnviewedStories(hasUnviewed)
                    .stories(storyResponses)
                    .build());
        }

        groups.sort(Comparator
                .comparing(CityStoryGroupResponse::isHasUnviewedStories).reversed()
                .thenComparing(group -> group.getStories().isEmpty()
                        ? LocalDateTime.MIN
                        : group.getStories().getLast().getCreatedAt(),
                        Comparator.reverseOrder()));

        return CityStoryFeedResponse.builder().cities(groups).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CityStoryItemResponse> getStoriesForLocation(UUID locationImageId, UUID userId) {
        locationImageRepository.findById(locationImageId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location not found"));

        LocalDateTime since = sinceWindow();
        Set<UUID> viewedIds = userId != null
                ? new HashSet<>(cityStoryMapper.selectViewedStoryIdsSince(userId, since))
                : Set.of();
        Set<UUID> likedIds = userId != null
                ? new HashSet<>(cityStoryMapper.selectLikedStoryIdsSince(userId, since))
                : Set.of();

        return cityStoryMapper.selectActiveByLocationSince(locationImageId, since).stream()
                .map(story -> mapStory(story, viewedIds, likedIds))
                .toList();
    }

    @Override
    @Transactional
    public CityStoryItemResponse createStory(UUID locationImageId, CreateCityStoryRequest request) {
        locationImageRepository.findById(locationImageId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location not found"));

        if (request.getPlaceId() != null) {
            placeRepository.findById(request.getPlaceId())
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Place not found"));
        }

        LocalDateTime now = LocalDateTime.now();
        CityStory story = CityStory.builder()
                .id(UUID.randomUUID())
                .locationImageId(locationImageId)
                .imageUrl(request.getImageUrl())
                .description(trimToNull(request.getDescription()))
                .placeId(request.getPlaceId())
                .likeCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        cityStoryMapper.insert(story);
        log.info("City story created: {} for location {}", story.getId(), locationImageId);
        return mapStory(story, Set.of(), Set.of());
    }

    @Override
    @Transactional
    public void deleteStory(UUID storyId) {
        CityStory story = cityStoryMapper.selectById(storyId);
        if (story == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Story not found");
        }

        LocalDateTime now = LocalDateTime.now();
        story.setDeletedAt(now);
        story.setUpdatedAt(now);
        cityStoryMapper.softDelete(story);
        log.info("City story deleted: {}", storyId);
    }

    @Override
    @Transactional
    public CityStoryLikeResponse toggleLike(UUID userId, UUID storyId) {
        CityStory story = cityStoryMapper.selectById(storyId);
        if (story == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Story not found");
        }

        boolean liked = cityStoryMapper.existsLike(storyId, userId);
        int likeCount = story.getLikeCount() != null ? story.getLikeCount() : 0;
        LocalDateTime now = LocalDateTime.now();

        if (liked) {
            cityStoryMapper.deleteLike(storyId, userId);
            likeCount = Math.max(0, likeCount - 1);
        } else {
            cityStoryMapper.insertLike(UUID.randomUUID(), storyId, userId, now);
            likeCount += 1;
        }

        story.setLikeCount(likeCount);
        story.setUpdatedAt(now);
        cityStoryMapper.updateLikeCount(story);

        return CityStoryLikeResponse.builder()
                .storyId(storyId)
                .likeCount(likeCount)
                .hasLiked(!liked)
                .build();
    }

    @Override
    @Transactional
    public void markViewed(UUID userId, UUID storyId) {
        CityStory story = cityStoryMapper.selectById(storyId);
        if (story == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Story not found");
        }

        if (!cityStoryMapper.existsView(storyId, userId)) {
            cityStoryMapper.insertView(UUID.randomUUID(), storyId, userId, LocalDateTime.now());
        }
    }

    private CityStoryItemResponse mapStory(
            CityStory story,
            Set<UUID> viewedIds,
            Set<UUID> likedIds) {
        return CityStoryItemResponse.builder()
                .id(story.getId())
                .locationImageId(story.getLocationImageId())
                .imageUrl(story.getImageUrl())
                .description(story.getDescription())
                .placeId(story.getPlaceId())
                .place(mapPlace(story.getPlaceId()))
                .likeCount(story.getLikeCount() != null ? story.getLikeCount() : 0)
                .hasLiked(likedIds.contains(story.getId()))
                .hasViewed(viewedIds.contains(story.getId()))
                .createdAt(story.getCreatedAt())
                .build();
    }

    private CityStoryPlaceSummary mapPlace(UUID placeId) {
        if (placeId == null) {
            return null;
        }
        return placeRepository.findById(placeId)
                .map(this::toPlaceSummary)
                .orElse(null);
    }

    private CityStoryPlaceSummary toPlaceSummary(Place place) {
        return CityStoryPlaceSummary.builder()
                .id(place.getId())
                .title(place.getTitle())
                .thumbnail(place.getThumbnail())
                .address(place.getAddress())
                .reviewRating(place.getReviewRating())
                .reviewCount(place.getReviewCount())
                .build();
    }

    private String resolveAvatarUrl(LocationImage location) {
        if (location.getAvatarUrl() != null && !location.getAvatarUrl().isBlank()) {
            return location.getAvatarUrl();
        }
        return location.getImageUrl();
    }

    private LocalDateTime sinceWindow() {
        return LocalDateTime.now().minusHours(STORY_TTL_HOURS);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
