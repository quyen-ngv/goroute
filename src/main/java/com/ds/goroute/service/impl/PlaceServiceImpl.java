package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.BatchUpdatePlaceImagesRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PlaceAboutDto;
import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.dto.response.FoodTagResponse;
import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.PlaceReviewService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.service.ImageMigrationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.FoodNameResolver;
import com.ds.goroute.utils.GeoDistanceUtils;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceServiceImpl implements PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final FoodRepository foodRepository;
    private final PlaceReviewService placeReviewService;
    private final ImageMigrationService imageMigrationService;
    private final PlaceSearchIndexService placeSearchIndexService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final PlaceTranslationService placeTranslationService;
    private final ObjectMapper objectMapper;

    private static final Integer maxReview = 50;
    private static final int MAX_PLACE_LUCENE_FETCH = 500;

    @Override
    @Transactional
    public PlaceResponse importPlace(ImportPlaceRequest request) {
        log.info("Importing place: {}", request.getPlaceId());

        try {
            // Migrate images before creating place (only if imageMigrationService is available)
            if (imageMigrationService != null) {
                String targetPath = "places/" + request.getPlaceId() + "/";

                // Migrate thumbnail
                if (request.getThumbnail() != null && !request.getThumbnail().trim().isEmpty()) {
                    try {
                        String newThumbnail = imageMigrationService.migrateImage(request.getThumbnail(), targetPath);
                        if (newThumbnail != null) {
                            request.setThumbnail(newThumbnail);
                        } else {
                            log.warn("Failed to migrate thumbnail for place: {}", request.getPlaceId());
                        }
                    } catch (Exception e) {
                        log.error("Error migrating thumbnail: {}", e.getMessage());
                    }
                }

                // Migrate place images
                if (request.getImages() != null && !request.getImages().trim().isEmpty() && !request.getImages().equals("[]")) {
                    try {
                        String newImages = imageMigrationService.migrateImagesJson(request.getImages(), targetPath);
                        if (!newImages.equals("[]")) {
                            request.setImages(newImages);
                        } else {
                            log.warn("No place images migrated successfully for: {}", request.getPlaceId());
                        }
                    } catch (Exception e) {
                        log.error("Error migrating place images: {}", e.getMessage());
                    }
                }

                // NOTE: Review images will be migrated by PlaceReviewService.batchInsertReviews()
                // No need to migrate here to avoid double migration
            } else {
                log.warn("ImageMigrationService not available, skipping image migration");
            }

            // Check if place already exists
            Place existingPlace = placeRepository.findByPlaceId(request.getPlaceId());
            if (existingPlace != null) {
                log.info("Place already exists, updating: {}", request.getPlaceId());
                return updateExistingPlace(existingPlace, request);
            }

            // Create new place
            Place place = buildPlaceFromRequest(request);
            placeRepository.insert(place);
            placeTranslationService.syncTranslations(place, request.getTranslations());
            placeSearchIndexService.indexPlace(place);

            // Import reviews using PlaceReviewService for smart duplicate handling
            if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
                importReviewsViaService(request.getUserReviews());
            }

            return toPlaceResponse(place);

        } catch (Exception e) {
            log.error("Failed to import place {}: {}", request.getPlaceId(), e.getMessage());
            return null; // Skip this place on error
        }
    }

    @Override
    @Transactional
    public List<PlaceResponse> importPlaces(List<ImportPlaceRequest> requests) {
        log.info("Importing {} places", requests.size());

        List<PlaceResponse> responses = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (ImportPlaceRequest request : requests) {
            PlaceResponse response = importPlace(request);
            if (response != null) {
                responses.add(response);
                successCount++;
            } else {
                failCount++;
                log.warn("Skipped place due to errors: {}", request.getPlaceId());
            }
        }

        log.info("Import completed: {} succeeded, {} failed", successCount, failCount);
        return responses;
    }

    @Override
    public PlaceResponse getPlaceById(UUID id) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        return toPlaceResponse(place);
    }

    @Override
    public PlaceResponse getPlaceByGoogleId(String placeId) {
        Place place = placeRepository.findByPlaceId(placeId);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        return toPlaceResponse(place);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceResponse> getAllPlaces() {
        return placeRepository.findAll().stream()
                .map(this::toPlaceResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radius, String category, List<String> placeGroups,
                                            BigDecimal minRating, int page, int size) {
        return searchPlaces(keyword, latitude, longitude, radius, category, placeGroups,
                minRating, null, null, null, page, size);
    }

    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radius, String category, List<String> placeGroups,
                                            BigDecimal minRating, String citySlug, List<UUID> foodIds,
                                            Boolean excludeLinkedFoodPlaces, int page, int size) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<Place> places = searchPlacesByTitleLucene(
                    keyword.trim(),
                    latitude,
                    longitude,
                    radius,
                    category,
                    placeGroups,
                    minRating,
                    citySlug,
                    foodIds,
                    excludeLinkedFoodPlaces,
                    page,
                    size);
            List<PlaceResponse> responses = places.stream()
                    .map(this::toPlaceResponse)
                    .collect(Collectors.toList());
            attachFoodTags(responses);
            return responses;
        }

        int offset = page * size;
        boolean extended = (foodIds != null && !foodIds.isEmpty())
                || (citySlug != null && !citySlug.isBlank())
                || Boolean.TRUE.equals(excludeLinkedFoodPlaces);

        String citySlugJson = null;
        if (citySlug != null && !citySlug.isBlank()) {
            citySlugJson = CitySlugResolver.toJsonbFilter(citySlug);
        }

        List<Place> places;
        if (extended) {
            places = placeRepository.findNearbyExtended(
                    null, latitude, longitude, radius, category, placeGroups, minRating,
                    citySlugJson, foodIds, excludeLinkedFoodPlaces, size, offset);
        } else {
            places = placeRepository.findNearby(
                    null, latitude, longitude, radius, category, placeGroups, minRating, size, offset);
        }

        List<PlaceResponse> responses = places.stream()
                .map(this::toPlaceResponse)
                .collect(Collectors.toList());
        attachFoodTags(responses);
        return responses;
    }

    private List<Place> searchPlacesByTitleLucene(
            String keyword,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radius,
            String category,
            List<String> placeGroups,
            BigDecimal minRating,
            String citySlug,
            List<UUID> foodIds,
            Boolean excludeLinkedFoodPlaces,
            int page,
            int size) {
        try {
            List<UUID> orderedIds = placeSearchIndexService.searchTitleIds(keyword, MAX_PLACE_LUCENE_FETCH);
            if (orderedIds.isEmpty()) {
                return List.of();
            }

            Map<UUID, Place> placesById = placeRepository.findByIds(orderedIds).stream()
                    .collect(Collectors.toMap(Place::getId, place -> place, (left, right) -> left));

            List<Place> matched = new ArrayList<>();
            for (UUID id : orderedIds) {
                Place place = placesById.get(id);
                if (place == null || !matchesPlaceSearchFilters(
                        place,
                        latitude,
                        longitude,
                        radius,
                        category,
                        placeGroups,
                        minRating,
                        citySlug)) {
                    continue;
                }
                matched.add(place);
            }

            if (Boolean.TRUE.equals(excludeLinkedFoodPlaces) || (foodIds != null && !foodIds.isEmpty())) {
                matched = applyExtendedFoodFilters(matched, foodIds, excludeLinkedFoodPlaces);
            }

            if (latitude != null && longitude != null) {
                for (Place place : matched) {
                    place.setDistance(GeoDistanceUtils.distanceKm(
                            latitude, longitude, place.getLatitude(), place.getLongitude()));
                }
                matched.sort(Comparator.comparing(Place::getDistance));
            }

            return matched.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Place Lucene search failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed");
        }
    }

    private boolean matchesPlaceSearchFilters(
            Place place,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radius,
            String category,
            List<String> placeGroups,
            BigDecimal minRating,
            String citySlug) {
        if (category != null && !category.isBlank()
                && (place.getCategory() == null || !category.equalsIgnoreCase(place.getCategory()))) {
            return false;
        }

        if (placeGroups != null && !placeGroups.isEmpty()) {
            PlaceGroup group = place.getPlaceGroup();
            if (group == null || placeGroups.stream().noneMatch(g -> g.equalsIgnoreCase(group.name()))) {
                return false;
            }
        }

        if (minRating != null
                && (place.getReviewRating() == null || place.getReviewRating().compareTo(minRating) < 0)) {
            return false;
        }

        if (latitude != null && longitude != null && radius != null) {
            double distanceKm = GeoDistanceUtils.distanceKm(
                    latitude, longitude, place.getLatitude(), place.getLongitude());
            if (distanceKm > radius.doubleValue()) {
                return false;
            }
        }

        if (citySlug != null && !citySlug.isBlank() && !matchesCitySlug(place, citySlug)) {
            return false;
        }

        return true;
    }

    private boolean matchesCitySlug(Place place, String citySlug) {
        String normalizedSlug = DestinationMatchUtils.normalizeKey(CitySlugResolver.normalizeRequired(citySlug));
        List<String> destinations = JsonUtils.fromJson(place.getDestinations(), new TypeReference<List<String>>() {});
        if (destinations == null || destinations.isEmpty()) {
            return false;
        }
        return destinations.stream()
                .map(DestinationMatchUtils::normalizeKey)
                .anyMatch(normalizedSlug::equals);
    }

    private List<Place> applyExtendedFoodFilters(
            List<Place> places,
            List<UUID> foodIds,
            Boolean excludeLinkedFoodPlaces) {
        if (places.isEmpty()) {
            return places;
        }

        List<UUID> placeIds = places.stream().map(Place::getId).filter(Objects::nonNull).toList();
        List<FoodTagRow> tagRows = foodRepository.findFoodTagsByPlaceIds(placeIds);
        Map<UUID, Set<UUID>> foodIdsByPlace = new HashMap<>();
        for (FoodTagRow row : tagRows) {
            foodIdsByPlace.computeIfAbsent(row.getPlaceId(), ignored -> new HashSet<>()).add(row.getFoodId());
        }

        Set<UUID> requiredFoodIds = foodIds == null
                ? Set.of()
                : foodIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());

        return places.stream()
                .filter(place -> {
                    Set<UUID> linkedFoodIds = foodIdsByPlace.getOrDefault(place.getId(), Set.of());
                    if (Boolean.TRUE.equals(excludeLinkedFoodPlaces) && !linkedFoodIds.isEmpty()) {
                        return false;
                    }
                    if (!requiredFoodIds.isEmpty()) {
                        return linkedFoodIds.stream().anyMatch(requiredFoodIds::contains);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void triggerSearchReindex() {
        placeSearchIndexService.triggerReindex();
    }

    private void attachFoodTags(List<PlaceResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<UUID> placeIds = responses.stream().map(PlaceResponse::getId).filter(Objects::nonNull).toList();
        List<FoodTagRow> tagRows = foodRepository.findFoodTagsByPlaceIds(placeIds);
        if (tagRows.isEmpty()) {
            return;
        }
        Map<UUID, List<FoodTagResponse>> byPlace = new HashMap<>();
        for (FoodTagRow row : tagRows) {
            Food food = Food.builder()
                    .nameVi(row.getNameVi())
                    .nameEn(row.getNameEn())
                    .nameJa(row.getNameJa())
                    .nameKo(row.getNameKo())
                    .build();
            FoodTagResponse tag = FoodTagResponse.builder()
                    .id(row.getFoodId())
                    .name(FoodNameResolver.resolveName(food))
                    .build();
            byPlace.computeIfAbsent(row.getPlaceId(), k -> new ArrayList<>()).add(tag);
        }
        for (PlaceResponse response : responses) {
            List<FoodTagResponse> tags = byPlace.get(response.getId());
            if (tags != null && !tags.isEmpty()) {
                response.setFoodTags(tags);
            }
        }
    }

    @Override
    public List<PlaceReviewResponse> getPlaceReviews(UUID placeId, int page, int size) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));

        int offset = page * size;
        
        // Simple paginated query sorted by authenticity_score DESC, review_date DESC
        List<PlaceReview> reviews = placeReviewRepository.findByPlaceIdPaginated(placeId, size, offset);

        return reviews.stream()
                .map(this::toReviewResponse)
                .collect(Collectors.toList());
    }

    /**
     * Calculate review distribution across star ratings
     * Min 1 review per star (if exists), remaining slots distributed by ratio
     */
    private Map<Integer, Integer> calculateReviewDistribution(Map<String, Integer> reviewsPerRating, int totalSlots) {
        Map<Integer, Integer> distribution = new HashMap<>();

        // Step 1: Allocate min 1 per star (if exists)
        int remainingSlots = totalSlots;
        for (int rating = 1; rating <= 5; rating++) {
            int count = reviewsPerRating.getOrDefault(String.valueOf(rating), 0);
            if (count > 0) {
                distribution.put(rating, 1);
                remainingSlots--;
            }
        }

        // Step 2: Calculate total reviews
        int totalReviews = reviewsPerRating.values().stream().mapToInt(Integer::intValue).sum();
        if (totalReviews == 0 || remainingSlots <= 0) {
            return distribution;
        }

        // Step 3: Calculate ratios
        Map<Integer, Double> ratios = new HashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            int count = reviewsPerRating.getOrDefault(String.valueOf(rating), 0);
            if (count > 0) {
                ratios.put(rating, (double) count / totalReviews);
            }
        }

        // Step 4: Distribute remaining slots proportionally
        Map<Integer, Double> exactAllocations = new HashMap<>();
        for (Map.Entry<Integer, Double> entry : ratios.entrySet()) {
            exactAllocations.put(entry.getKey(), entry.getValue() * remainingSlots);
        }

        // Allocate integer parts first
        int allocated = 0;
        Map<Integer, Double> remainders = new HashMap<>();

        for (Map.Entry<Integer, Double> entry : exactAllocations.entrySet()) {
            int rating = entry.getKey();
            double exact = entry.getValue();
            int intPart = (int) exact;
            double remainder = exact - intPart;

            distribution.put(rating, distribution.get(rating) + intPart);
            allocated += intPart;

            if (remainder > 0.001) { // Avoid floating point errors
                remainders.put(rating, remainder);
            }
        }

        // Distribute remaining slots by largest remainder
        int leftover = remainingSlots - allocated;
        if (leftover > 0 && !remainders.isEmpty()) {
            List<Map.Entry<Integer, Double>> sortedRemainders = remainders.entrySet().stream()
                    .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                    .collect(Collectors.toList());

            for (int i = 0; i < Math.min(leftover, sortedRemainders.size()); i++) {
                int rating = sortedRemainders.get(i).getKey();
                distribution.put(rating, distribution.get(rating) + 1);
            }
        }

        return distribution;
    }

    private int compareNullable(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    @Override
    @Transactional
    public void deletePlace(UUID id) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));

        imageStorageCleanupService.deleteImagesForEntityRecord("PLACE", id);
        // Delete reviews first
        placeReviewRepository.deleteByPlaceId(id);

        // Delete place
        placeRepository.delete(id);
        placeSearchIndexService.deletePlace(id);
        log.info("Deleted place: {}", id);
    }

    @Override
    @Transactional
    public PlaceResponse updatePlace(UUID id, UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));

        // Update all fields
        place.setTitle(request.getTitle());
        place.setCategory(request.getCategory());
        place.setPlaceGroup(request.getPlaceGroup() != null ?
                com.ds.goroute.type.PlaceGroup.valueOf(request.getPlaceGroup()) : null);
        place.setAddress(request.getAddress());
        place.setDestinations(toJson(request.getDestinations()));
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setPlusCode(request.getPlusCode());
        place.setTimezone(request.getTimezone());
        place.setPhone(request.getPhone());
        place.setWebsite(request.getWebsite());
        place.setGoogleMapsLink(request.getGoogleMapsLink());
        place.setReviewCount(request.getReviewCount());
        place.setReviewRating(request.getReviewRating());
        place.setReviewsPerRating(request.getReviewsPerRating());
        place.setThumbnail(request.getThumbnail());
        place.setImages(request.getImages());
        place.setDescriptions(request.getDescriptions());
        place.setStatus(request.getStatus());
        place.setPriceRange(request.getPriceRange());
        place.setOpenHours(request.getOpenHours());
        place.setPopularTimes(request.getPopularTimes());
        place.setReservations(request.getReservations());
        place.setOrderOnline(request.getOrderOnline());
        place.setMenu(request.getMenu());
        place.setCompleteAddress(request.getCompleteAddress());
        place.setAbout(request.getAbout());
        place.setOwner(request.getOwner());
        place.setEmails(request.getEmails());
        place.setUpdatedAt(LocalDateTime.now());

        placeRepository.update(place);
        placeTranslationService.syncTranslations(place, request.getTranslations());
        placeSearchIndexService.indexPlace(place);
        log.info("Updated place: {}", id);

        return toPlaceResponse(place);
    }

    // Helper methods

    private PlaceResponse updateExistingPlace(Place existingPlace, ImportPlaceRequest request) {
        Place updated = buildPlaceFromRequest(request);
        updated.setId(existingPlace.getId());
        updated.setCreatedAt(existingPlace.getCreatedAt());
        updated.setCategory(existingPlace.getCategory() != null ? existingPlace.getCategory() : request.getCategory());
        updated.setDescriptions(existingPlace.getDescriptions() != null ? existingPlace.getDescriptions() : request.getDescriptions());
        updated.setUpdatedAt(LocalDateTime.now());

        placeRepository.update(updated);
        placeTranslationService.syncTranslations(updated, request.getTranslations());
        placeSearchIndexService.indexPlace(updated);

        // Update reviews using PlaceReviewService for smart duplicate handling
        if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
            importReviewsViaService(request.getUserReviews());
        }

        return toPlaceResponse(updated);
    }

    private Place buildPlaceFromRequest(ImportPlaceRequest request) {
        return Place.builder()
                .id(UUID.randomUUID())
                .placeId(request.getPlaceId())
                .cid(request.getCid())
                .dataId(request.getDataId())
                .title(request.getTitle())
                .category(request.getCategory())
                .placeGroup(request.getPlaceGroup() != null ?
                        com.ds.goroute.type.PlaceGroup.valueOf(request.getPlaceGroup()) : null)
                .address(request.getAddress())
                .destinations(toJson(request.getDestinations()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .plusCode(request.getPlusCode())
                .timezone(request.getTimezone())
                .phone(request.getPhone())
                .website(request.getWebsite())
                .googleMapsLink(request.getGoogleMapsLink())
                .reviewCount(request.getReviewCount())
                .reviewRating(request.getReviewRating())
                .reviewsPerRating(request.getReviewsPerRating())
                .thumbnail(request.getThumbnail())
                .images(request.getImages())
                .descriptions(request.getDescriptions())
                .status(request.getStatus())
                .priceRange(request.getPriceRange())
                .openHours(request.getOpenHours())
                .popularTimes(request.getPopularTimes())
                .reservations(request.getReservations())
                .orderOnline(request.getOrderOnline())
                .menu(request.getMenu())
                .completeAddress(request.getCompleteAddress())
                .about(request.getAbout())
                .owner(request.getOwner())
                .emails(request.getEmails())
                .rawData(request.getRawData())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Migrate review images within reviews JSON
     * DEPRECATED: No longer used. Review migration is handled by PlaceReviewService.batchInsertReviews()
     */
    @Deprecated
    private String migrateReviewImages(String reviewsJson, String targetPath) {
        try {
            JsonNode reviewsNode = objectMapper.readTree(reviewsJson);
            if (!reviewsNode.isArray()) {
                return reviewsJson;
            }

            ArrayNode resultArray = objectMapper.createArrayNode();

            for (JsonNode reviewNode : reviewsNode) {
                ObjectNode mutableReview = ((ObjectNode) reviewNode).deepCopy();

                // Migrate profilePicture if exists
                if (reviewNode.has("profilePicture") && reviewNode.get("profilePicture").isTextual()) {
                    String profilePic = reviewNode.get("profilePicture").asText();
                    if (profilePic != null && !profilePic.isEmpty()) {
                        String newProfilePic = imageMigrationService.migrateImage(profilePic, targetPath);
                        if (newProfilePic != null) {
                            mutableReview.put("profilePicture", newProfilePic);
                        }
                    }
                }

                // Migrate images field if exists
                if (reviewNode.has("images") && reviewNode.get("images").isArray()) {
                    ArrayNode imagesArray = (ArrayNode) reviewNode.get("images");
                    List<String> imageUrls = new ArrayList<>();

                    for (JsonNode imgNode : imagesArray) {
                        if (imgNode.isTextual()) {
                            imageUrls.add(imgNode.asText());
                        }
                    }

                    if (!imageUrls.isEmpty()) {
                        Map<String, String> migratedUrls = imageMigrationService.migrateImages(imageUrls, targetPath);

                        // Build new images array with migrated URLs
                        ArrayNode newImagesArray = objectMapper.createArrayNode();
                        for (String oldUrl : imageUrls) {
                            String newUrl = migratedUrls.get(oldUrl);
                            if (newUrl != null) {
                                newImagesArray.add(newUrl);
                            }
                        }

                        mutableReview.set("images", newImagesArray);
                    }
                }

                resultArray.add(mutableReview);
            }

            return objectMapper.writeValueAsString(resultArray);

        } catch (Exception e) {
            log.error("Error migrating review images: {}", e.getMessage());
            return reviewsJson; // Return original on error
        }
    }

    /**
     * Import reviews via PlaceReviewService to leverage smart duplicate handling
     * Converts old JSON format to ReviewInput format
     */
    private void importReviewsViaService(String reviewsJson) {
        try {
            JsonNode reviewsNode = objectMapper.readTree(reviewsJson);
            if (!reviewsNode.isArray()) {
                return;
            }

            List<ReviewInput> reviewInputs = new ArrayList<>();

            for (JsonNode reviewNode : reviewsNode) {
                try {
                    // Extract required fields
                    String reviewId = getTextValue(reviewNode, "reviewId");
                    String googlePlaceId = getTextValue(reviewNode, "googlePlaceId");
                    String authorName = getTextValue(reviewNode, "name");

                    // Skip if missing required fields
                    if (reviewId == null || googlePlaceId == null || authorName == null) {
                        log.warn("Skipping review with missing required fields");
                        continue;
                    }

                    // Extract rating
                    Integer rating = null;
                    if (reviewNode.has("rating") && !reviewNode.get("rating").isNull()) {
                        int ratingValue = reviewNode.get("rating").asInt();
                        if (ratingValue >= 1 && ratingValue <= 5) {
                            rating = ratingValue;
                        }
                    }

                    if (rating == null) {
                        log.warn("Skipping review {} with invalid rating", reviewId);
                        continue;
                    }

                    // Extract description and build reviewText map
                    String description = getTextValue(reviewNode, "description");
                    Map<String, String> reviewText = new HashMap<>();
                    if (description != null && !description.isEmpty()) {
                        // Detect language or default to "en"
                        String language = detectLanguage(description);
                        reviewText.put(language, description);
                    }

                    // Extract images
                    List<String> userImages = new ArrayList<>();
                    if (reviewNode.has("images") && reviewNode.get("images").isArray()) {
                        for (JsonNode imgNode : reviewNode.get("images")) {
                            if (imgNode.isTextual()) {
                                userImages.add(imgNode.asText());
                            }
                        }
                    }

                    // Parse review date
                    String reviewDate = getTextValue(reviewNode, "when");
                    if (reviewDate == null || reviewDate.isEmpty()) {
                        reviewDate = LocalDateTime.now().toString(); // Fallback to now
                    } else {
                        // Try to convert old format to ISO 8601
                        reviewDate = convertToIso8601(reviewDate);
                    }

                    // Build ReviewInput
                    ReviewInput reviewInput = ReviewInput.builder()
                            .reviewId(reviewId)
                            .googlePlaceId(googlePlaceId)
                            .authorName(authorName)
                            .profileUrl(getTextValue(reviewNode, "profileUrl"))
                            .profilePicture(getTextValue(reviewNode, "profilePicture"))
                            .isLocalGuide(reviewNode.has("isLocalGuide") && reviewNode.get("isLocalGuide").asBoolean())
                            .totalReviews(reviewNode.has("totalReviews") ? reviewNode.get("totalReviews").asInt() : 0)
                            .totalPhotos(reviewNode.has("totalPhotos") ? reviewNode.get("totalPhotos").asInt() : 0)
                            .rating(rating)
                            .reviewText(reviewText)
                            .reviewDate(reviewDate)
                            .userImages(userImages)
                            .likes(reviewNode.has("likes") ? reviewNode.get("likes").asInt() : 0)
                            .contentHash(generateContentHash(authorName, rating, description, reviewDate))
                            .isDeleted(false)
                            .build();

                    reviewInputs.add(reviewInput);

                } catch (Exception e) {
                    log.error("Error parsing review node: {}", e.getMessage());
                }
            }

            // Call PlaceReviewService to handle batch insert with duplicate checking
            if (!reviewInputs.isEmpty()) {
                Map<String, Object> result = placeReviewService.batchInsertReviews(reviewInputs);
                log.info("Review import result: {}", result);
            }

        } catch (Exception e) {
            log.error("Error parsing reviews JSON: {}", e.getMessage());
        }
    }

    /**
     * Detect language from text content (basic heuristic)
     */
    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return "en";
        }

        // Simple heuristic: check for Vietnamese characters
        if (text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ].*")) {
            return "vi";
        }

        // Check for Japanese characters
        if (text.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF].*")) {
            return "ja";
        }

        // Check for Korean characters
        if (text.matches(".*[\\uAC00-\\uD7AF].*")) {
            return "ko";
        }

        // Check for Chinese characters
        if (text.matches(".*[\\u4E00-\\u9FFF].*")) {
            return "zh";
        }

        return "en"; // Default to English
    }

    /**
     * Convert old date format to ISO 8601
     */
    private String convertToIso8601(String dateStr) {
        try {
            // Try parsing "yyyy-M-d" format
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"));
            return date.atStartOfDay().toString();
        } catch (Exception e1) {
            try {
                // Try ISO 8601 format (already correct)
                LocalDateTime.parse(dateStr);
                return dateStr;
            } catch (Exception e2) {
                // Return current timestamp as fallback
                return LocalDateTime.now().toString();
            }
        }
    }

    /**
     * Generate content hash for duplicate detection
     */
    private String generateContentHash(String authorName, Integer rating, String description, String reviewDate) {
        String content = String.format("%s|%d|%s|%s",
                authorName != null ? authorName : "",
                rating != null ? rating : 0,
                description != null ? description : "",
                reviewDate != null ? reviewDate : ""
        );
        return String.valueOf(content.hashCode());
    }

    @Deprecated
    private void importReviews(UUID placeId, String reviewsJson) {
        try {
            JsonNode reviewsNode = objectMapper.readTree(reviewsJson);
            if (!reviewsNode.isArray()) {
                return;
            }

            List<PlaceReview> reviews = new ArrayList<>();

            for (JsonNode reviewNode : reviewsNode) {
                try {
                    Integer rating = null;
                    if (reviewNode.has("rating") && !reviewNode.get("rating").isNull()) {
                        int ratingValue = reviewNode.get("rating").asInt();
                        if (ratingValue >= 1 && ratingValue <= 5) {
                            rating = ratingValue;
                        }
                    }

                    PlaceReview review = PlaceReview.builder()
                            .id(UUID.randomUUID())
                            .placeId(placeId)
                            .reviewerName(getTextValue(reviewNode, "name"))
                            .profilePicture(getTextValue(reviewNode, "profilePicture"))
                            .rating(rating)
                            .description(getTextValue(reviewNode, "description"))
                            .reviewDate(parseReviewDate(getTextValue(reviewNode, "when")))
                            .images(reviewNode.has("images") ? reviewNode.get("images").toString() : null)
                            .createdAt(LocalDateTime.now())
                            .build();

                    reviews.add(review);
                } catch (Exception e) {
                    log.error("Error parsing review: {}", e.getMessage());
                }
            }

            if (!reviews.isEmpty()) {
                placeReviewRepository.insertBatch(reviews);
                log.info("Imported {} reviews for place {}", reviews.size(), placeId);
            }
        } catch (Exception e) {
            log.error("Error parsing reviews JSON: {}", e.getMessage());
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asText()
                : null;
    }

    private LocalDate parseReviewDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Try format: "25-6-1"
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"));
        } catch (Exception e) {
            log.warn("Could not parse review date: {}", dateStr);
            return null;
        }
    }

    private PlaceResponse toPlaceResponse(Place place) {
        PlaceTranslation translation = placeTranslationService.resolve(place.getId(), AcceptLanguageFilter.currentCode());
        String resolvedTitle = translation != null && translation.getName() != null
                ? translation.getName()
                : place.getTitle();
        String resolvedDescription = translation != null && translation.getDescription() != null
                ? translation.getDescription()
                : place.getDescriptions();
        return PlaceResponse.builder()
                .id(place.getId())
                .placeId(place.getPlaceId())
                .locale(translation != null ? translation.getLocale().code() : null)
                .name(resolvedTitle)
                .title(resolvedTitle)
                .translations(placeTranslationService.allResponses(place.getId()))
                .category(place.getCategory())
                .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : null)
                .address(place.getAddress())
                .destinations(parseJsonToStringList(place.getDestinations()))
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .phone(place.getPhone())
                .website(place.getWebsite())
                .googleMapsLink(place.getGoogleMapsLink())
                .reviewCount(place.getReviewCount())
                .reviewRating(place.getReviewRating())
                .adjustedRating(place.getAdjustedRating())
                .placeOverallScore(place.getPlaceOverallScore())
                .reviewsPerRating(parseJsonToMap(place.getReviewsPerRating()))
                .thumbnail(place.getThumbnail())
                .images(parseJsonToList(place.getImages(), PlaceImagesDto.class))
                .descriptions(resolvedDescription)
                .priceRange(place.getPriceRange())
                .openHours(parseJsonToMapOfList(place.getOpenHours()))
                .popularTimes(parseJsonToMapOfMap(place.getPopularTimes()))
                .about(parseJsonToList(place.getAbout(), PlaceAboutDto.class))
                .distance(place.getDistance())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }

    private Map<String, Integer> parseJsonToMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map: {}", e.getMessage());
            return null;
        }
    }

    private <T> List<T> parseJsonToList(String jsonString, Class<T> clazz) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.warn("Failed to parse JSON to List: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseJsonToStringList(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonString,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse destinations JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize value to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private Map<String, List<String>> parseJsonToMapOfList(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<String>>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map<String, List<String>>: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Map<String, Integer>> parseJsonToMapOfMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Integer>>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map<String, Map<String, Integer>>: {}", e.getMessage());
            return null;
        }
    }

    private PlaceReviewResponse toReviewResponse(PlaceReview review) {
        return PlaceReviewResponse.builder()
                .id(review.getId())
                .placeId(review.getPlaceId())
                .reviewerName(review.getReviewerName())
                .profilePicture(review.getProfilePicture())
                .profileUrl(review.getProfileUrl())
                .isLocalGuide(review.getIsLocalGuide())
                .totalReviews(review.getTotalReviews())
                .totalPhotos(review.getTotalPhotos())
                .rating(review.getRating())
                .description(review.getDescription())
                .reviewDate(review.getReviewDate())
                .images(review.getImages())
                .likes(review.getLikes())
                .authenticityScore(review.getAuthenticityScore())
                .authenticityLevel(review.getAuthenticityLevel() != null ? review.getAuthenticityLevel().name() : null)
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> batchUpdatePlaceImages(BatchUpdatePlaceImagesRequest request) {
        log.info("Batch updating images for {} places", request.getPlaces().size());

        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (BatchUpdatePlaceImagesRequest.PlaceImageUpdate update : request.getPlaces()) {
            try {
                Place place = placeRepository.findById(update.getId())
                        .orElseThrow(() -> new BusinessException(
                                ErrorConstant.PLACE_NOT_FOUND,
                                "Place not found: " + update.getId()));

                boolean hasChanges = false;

                // Update thumbnail if provided
                if (update.getThumbnail() != null && !update.getThumbnail().isEmpty()) {
                    place.setThumbnail(update.getThumbnail());
                    hasChanges = true;
                }

                // Update images if provided
                if (update.getImages() != null && !update.getImages().isEmpty()) {
                    place.setImages(update.getImages());
                    hasChanges = true;
                }

                if (hasChanges) {
                    place.setUpdatedAt(LocalDateTime.now());
                    placeRepository.update(place);
                    updated++;

                    log.debug("Updated images for place: {} ({})",
                            place.getTitle(), place.getPlaceId());
                }

            } catch (Exception e) {
                failed++;
                String errorMsg = "Failed to update place " + update.getId() + ": " + e.getMessage();
                errors.add(errorMsg);
                log.error(errorMsg, e);
            }
        }

        log.info("Batch update completed: {} updated, {} failed", updated, failed);

        Map<String, Object> result = new HashMap<>();
        result.put("totalInput", request.getPlaces().size());
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("errors", errors);

        return result;
    }
}
