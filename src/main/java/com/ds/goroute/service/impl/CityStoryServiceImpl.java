package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateCityStoryRequest;
import com.ds.goroute.dto.response.CityStoryFeedResponse;
import com.ds.goroute.dto.response.CityStoryGroupResponse;
import com.ds.goroute.dto.response.CityStoryItemResponse;
import com.ds.goroute.dto.response.CityStoryLikeResponse;
import com.ds.goroute.dto.response.CityStoryPlaceSummary;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.CityStory;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.CityStoryMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.CityStoryService;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.ImageStorageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CityStoryServiceImpl implements CityStoryService {

    private static final int STORY_TTL_HOURS = 24;

    private final CityStoryMapper cityStoryMapper;
    private final LocationImageRepository locationImageRepository;
    private final PlaceRepository placeRepository;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final CityWeatherService cityWeatherService;
    private final Executor applicationTaskExecutor;

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

        Map<UUID, CityWeatherResponse> weatherByLocation =
                fetchWeather(grouped.keySet(), locationsById);

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
                    .weather(weatherByLocation.get(location.getId()))
                    .build());
        }

        groups.sort(Comparator
                .comparing(CityStoryGroupResponse::isHasUnviewedStories).reversed()
                .thenComparing(group -> group.getStories().isEmpty()
                        ? Instant.MIN
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

        String mediaType = normalizeMediaType(request.getMediaType());
        String imageUrl = trimToNull(request.getImageUrl());
        String videoUrl = trimToNull(request.getVideoUrl());
        if (mediaType.equals("VIDEO") ? videoUrl == null : imageUrl == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    mediaType.equals("VIDEO") ? "videoUrl is required for a video story" : "imageUrl is required for an image story");
        }

        LocalDateTime now = nowUtc();
        CityStory story = CityStory.builder()
                .id(UUID.randomUUID())
                .locationImageId(locationImageId)
                .imageUrl(imageUrl)
                .mediaType(mediaType)
                .videoUrl(videoUrl)
                .thumbnailUrl(trimToNull(request.getThumbnailUrl()))
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

        imageStorageCleanupService.deleteImagesForEntityRecord("CITY_STORY", storyId);
        LocalDateTime now = nowUtc();
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
        LocalDateTime now = nowUtc();

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
            cityStoryMapper.insertView(UUID.randomUUID(), storyId, userId, nowUtc());
        }
    }

    /**
     * Weather for every city that has stories, fetched in parallel.
     *
     * <p>Sequentially this would be one upstream round-trip per city on a cold cache,
     * which the feed pays for on the first request of every 30-minute window. Each
     * lookup is independent, never touches the database, and swallows its own failures,
     * so a city simply comes back without weather rather than failing the feed.
     */
    private Map<UUID, CityWeatherResponse> fetchWeather(
            Set<UUID> locationIds,
            Map<UUID, LocationImage> locationsById) {
        List<LocationImage> locations = locationIds.stream()
                .map(locationsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (locations.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CityWeatherResponse> weatherByLocation = new ConcurrentHashMap<>();
        CompletableFuture.allOf(locations.stream()
                .map(location -> CompletableFuture.runAsync(() -> {
                    CityWeatherResponse weather = cityWeatherService.findWeatherQuietly(location);
                    if (weather != null) {
                        weatherByLocation.put(location.getId(), weather);
                    }
                }, applicationTaskExecutor))
                .toArray(CompletableFuture[]::new))
            .join();
        return weatherByLocation;
    }

    private CityStoryItemResponse mapStory(
            CityStory story,
            Set<UUID> viewedIds,
            Set<UUID> likedIds) {
        return CityStoryItemResponse.builder()
                .id(story.getId())
                .locationImageId(story.getLocationImageId())
                .imageUrl(story.getImageUrl())
                .mediaType(story.getMediaType())
                .videoUrl(story.getVideoUrl())
                .thumbnailUrl(story.getThumbnailUrl())
                .description(story.getDescription())
                .placeId(story.getPlaceId())
                .place(mapPlace(story.getPlaceId()))
                .likeCount(story.getLikeCount() != null ? story.getLikeCount() : 0)
                .hasLiked(likedIds.contains(story.getId()))
                .hasViewed(viewedIds.contains(story.getId()))
                .createdAt(toInstant(story.getCreatedAt()))
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
                .adjustedRating(place.getAdjustedRating())
                .reviewCount(place.getReviewCount())
                .build();
    }

    private String resolveAvatarUrl(LocationImage location) {
        if (location.getAvatarUrl() != null && !location.getAvatarUrl().isBlank()) {
            return location.getAvatarUrl();
        }
        return location.getImageUrl();
    }

    /**
     * Stamps the stored wall clock as the UTC instant it is.
     *
     * <p>Every city-story timestamp is written against {@link #nowUtc()}, so the naive
     * value in the column is already UTC and only needs saying so.
     */
    private Instant toInstant(LocalDateTime storedUtc) {
        return storedUtc == null ? null : storedUtc.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime sinceWindow() {
        return nowUtc().minusHours(STORY_TTL_HOURS);
    }

    /**
     * The clock every city-story timestamp is written and compared against.
     *
     * <p>{@code createdAt} goes out over the wire without an offset, and the client
     * reads an offset-less timestamp as UTC. Plain {@code LocalDateTime.now()} follows
     * the JVM's default zone instead, so on any host that is not on UTC every story
     * arrived stamped in the future and the feed showed all of them as just posted.
     * The 24-hour window is read off the same clock so the two cannot drift apart.
     */
    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "IMAGE";
        }
        String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!upper.equals("IMAGE") && !upper.equals("VIDEO")) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "mediaType must be IMAGE or VIDEO");
        }
        return upper;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
