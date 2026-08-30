package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.RecentSavedPlaceResponse;
import com.ds.goroute.dto.response.SavedPlaceCategoryResponse;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.dto.response.SavedItemsOverviewResponse;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.SavedPlaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPlaceServiceImpl implements SavedPlaceService {

    private static final String DEFAULT_ITEM_TYPE = "PLACE";
    private static final Set<String> PLACE_ITEM_TYPES = Set.of("PLACE", "FOOD");

    private final SavedPlaceMapper savedPlaceMapper;
    private final SocialLocationJobMapper socialLocationJobMapper;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final ObjectMapper objectMapper;

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
        SavedPlace existing = savedPlaceMapper.findByUserIdAndPlaceId(
                userId,
                request.getPlaceId(),
                itemType,
                request.getCategory());
        if (existing != null) {
            return toResponse(existing);
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
    @Transactional(readOnly = true)
    public List<RecentSavedPlaceResponse> getRecentSavedPlaces(UUID userId) {
        Map<String, RecentSavedPlaceAccumulator> places = new LinkedHashMap<>();

        for (SavedPlace savedPlace : savedPlaceMapper.findAllByUserId(userId)) {
            if (!PLACE_ITEM_TYPES.contains(normalizeItemType(savedPlace.getItemType()))) {
                continue;
            }
            mergePlace(
                    places,
                    savedPlace.getPlaceId(),
                    savedPlace.getName(),
                    savedPlace.getAddress(),
                    savedPlace.getLat(),
                    savedPlace.getLng(),
                    savedPlace.getRating(),
                    savedPlace.getPhotoUrl(),
                    savedPlace.getCategory(),
                    null,
                    savedPlace.getCreatedAt());
        }

        for (SocialLocationJob job : socialLocationJobMapper.findAllCompletedByUserId(userId)) {
            mergeMappedSocialPlaces(places, job);
        }

        return places.values().stream()
                .map(RecentSavedPlaceAccumulator::toResponse)
                .sorted((first, second) -> compareDatesDescending(first.getSavedAt(), second.getSavedAt()))
                .toList();
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

    private void mergeMappedSocialPlaces(
            Map<String, RecentSavedPlaceAccumulator> places,
            SocialLocationJob job) {
        JsonNode result = parseJson(job.getResultPayload());
        JsonNode candidates = result == null ? null : result.path("extraction").path("candidates");
        if (candidates == null || !candidates.isArray()) {
            return;
        }

        LocalDateTime savedAt = job.getCompletedAt() != null
                ? job.getCompletedAt()
                : job.getCreatedAt();
        for (JsonNode candidate : candidates) {
            JsonNode mappedCandidate = firstMappedCandidate(candidate.path("mapSearch").path("candidates"));
            if (mappedCandidate == null) {
                continue;
            }
            JsonNode mapping = mappedCandidate.path("placeMapping");
            String placeId = text(mapping, "placeId");
            if (placeId == null) {
                continue;
            }

            mergePlace(
                    places,
                    placeId,
                    firstText(text(mappedCandidate, "title"), text(candidate, "name"), text(candidate, "query")),
                    firstText(text(mappedCandidate, "address"), text(candidate, "address_hint")),
                    number(mappedCandidate, "latitude"),
                    number(mappedCandidate, "longitude"),
                    null,
                    firstText(
                            text(mappedCandidate, "thumbnail"),
                            text(mappedCandidate, "thumbnailUrl"),
                            text(mappedCandidate, "imageUrl"),
                            text(mappedCandidate, "photoUrl"),
                            text(mapping, "thumbnail")),
                    firstText(text(mappedCandidate, "category"), text(candidate, "category")),
                    text(candidate, "description"),
                    savedAt);
        }
    }

    private JsonNode firstMappedCandidate(JsonNode candidates) {
        if (candidates == null || !candidates.isArray()) {
            return null;
        }
        for (JsonNode candidate : candidates) {
            if (text(candidate.path("placeMapping"), "placeId") != null) {
                return candidate;
            }
        }
        return null;
    }

    private void mergePlace(
            Map<String, RecentSavedPlaceAccumulator> places,
            String placeId,
            String name,
            String address,
            Double lat,
            Double lng,
            Double rating,
            String photoUrl,
            String category,
            String categoryDescription,
            LocalDateTime savedAt) {
        String normalizedPlaceId = normalizeText(placeId);
        if (normalizedPlaceId == null) {
            return;
        }
        places.computeIfAbsent(normalizedPlaceId, RecentSavedPlaceAccumulator::new)
                .merge(name, address, lat, lng, rating, photoUrl, category, categoryDescription, savedAt);
    }

    private JsonNode parseJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            log.warn("Failed to read a social-location job result while building recent saves: {}",
                    exception.getMessage(), exception);
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return normalizeText(node.path(field).asText(null));
    }

    private String firstText(String... values) {
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private Double number(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return value.isTextual() ? Double.parseDouble(value.asText()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static int compareDatesDescending(LocalDateTime first, LocalDateTime second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return second.compareTo(first);
    }

    private static final class RecentSavedPlaceAccumulator {
        private final String placeId;
        private String name;
        private String address;
        private Double lat;
        private Double lng;
        private Double rating;
        private String photoUrl;
        private LocalDateTime savedAt;
        private final Map<String, SavedPlaceCategoryResponse> categories = new LinkedHashMap<>();

        private RecentSavedPlaceAccumulator(String placeId) {
            this.placeId = placeId;
        }

        private void merge(
                String name,
                String address,
                Double lat,
                Double lng,
                Double rating,
                String photoUrl,
                String category,
                String categoryDescription,
                LocalDateTime savedAt) {
            if (this.name == null) this.name = name;
            if (this.address == null) this.address = address;
            if (this.lat == null) this.lat = lat;
            if (this.lng == null) this.lng = lng;
            if (this.rating == null) this.rating = rating;
            if (this.photoUrl == null) this.photoUrl = photoUrl;
            if (this.savedAt == null || (savedAt != null && savedAt.isAfter(this.savedAt))) {
                this.savedAt = savedAt;
            }

            String normalizedCategory = category == null ? null : category.trim();
            String categoryKey = normalizedCategory == null || normalizedCategory.isEmpty()
                    ? ""
                    : normalizedCategory.toLowerCase();
            SavedPlaceCategoryResponse existing = categories.get(categoryKey);
            if (existing == null) {
                categories.put(categoryKey, SavedPlaceCategoryResponse.builder()
                        .name(normalizedCategory == null || normalizedCategory.isEmpty() ? null : normalizedCategory)
                        .description(categoryDescription)
                        .build());
            } else if ((existing.getDescription() == null || existing.getDescription().isBlank())
                    && categoryDescription != null && !categoryDescription.isBlank()) {
                existing.setDescription(categoryDescription);
            }
        }

        private RecentSavedPlaceResponse toResponse() {
            return RecentSavedPlaceResponse.builder()
                    .placeId(placeId)
                    .name(name == null ? placeId : name)
                    .address(address)
                    .lat(lat)
                    .lng(lng)
                    .rating(rating)
                    .photoUrl(photoUrl)
                    .categories(List.copyOf(categories.values()))
                    .savedAt(savedAt)
                    .build();
        }
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
