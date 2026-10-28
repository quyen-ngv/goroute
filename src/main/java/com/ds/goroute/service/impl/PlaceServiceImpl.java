package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.BatchUpdatePlaceImagesRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.dto.response.PlaceAboutDto;
import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.dto.response.AdminPlaceResponse;
import com.ds.goroute.dto.response.AdminPlacePageResponse;
import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.PlaceMenuDto;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.dto.response.PlaceTranslationResponse;
import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.entity.PlaceTrendingRow;
import com.ds.goroute.event.PlaceActivatedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.dto.response.FoodTagResponse;
import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.repository.PlaceSourceRepository;
import com.ds.goroute.service.LocationAreaService;
import com.ds.goroute.service.PlaceReviewService;
import com.ds.goroute.service.PlaceAttributeCatalog;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.PlaceSocialVideoService;
import com.ds.goroute.service.PlaceMapTileService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.service.ImageMigrationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.utils.FoodNameResolver;
import com.ds.goroute.utils.AdminListSort;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.PlaceDisplayRating;
import com.ds.goroute.utils.PlaceIdentityMatcher;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.type.PlaceTrendingPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceServiceImpl implements PlaceService {

    /** Hard ceiling on one detail-refresh sweep, matching CreatePlaceDetailRefreshJobRequest.maxPlaces. */
    private static final int DETAIL_REFRESH_CANDIDATE_LIMIT = 10000;

    /**
     * A drawn verification area larger than this is almost certainly a mis-click that
     * traced a district rather than a lake or a quarter. Hạ Long Bay is ~1,500 km², but
     * a bay is not one place to check in at; the largest real case (Hoàn Kiếm, an old
     * quarter, a national park entrance) is well under this.
     */
    private static final double MAX_VERIFICATION_AREA_KM2 = 50d;

    /**
     * Null means "not sent": the existing area stays. A JSON null clears it. Anything else
     * must be a valid Polygon/MultiPolygon as PostGIS judges it, within the size ceiling.
     */
    private void applyVerificationGeometry(Place place, JsonNode geometry) {
        if (geometry == null) {
            return;
        }
        if (geometry.isNull()) {
            placeRepository.updateVerificationGeometry(place.getId(), null);
            place.setVerificationGeometryGeoJson(null);
            return;
        }
        String geoJson = geometry.toString();
        PlaceRepository.GeometryValidation validation;
        try {
            validation = placeRepository.validateGeometry(geoJson);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "verificationGeometry is not a GeoJSON geometry PostGIS can read");
        }
        if (!validation.valid()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "verificationGeometry is not a valid polygon: " + validation.reason());
        }
        String type = validation.geometryType() == null ? "" : validation.geometryType().toUpperCase();
        if (!type.equals("POLYGON") && !type.equals("MULTIPOLYGON")) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "verificationGeometry must be a Polygon or MultiPolygon, not " + validation.geometryType());
        }
        if (validation.areaKm2() > MAX_VERIFICATION_AREA_KM2) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, String.format(
                    "verificationGeometry covers %.1f km²; the limit is %.0f km²",
                    validation.areaKm2(), MAX_VERIFICATION_AREA_KM2));
        }
        placeRepository.updateVerificationGeometry(place.getId(), geoJson);
        place.setVerificationGeometryGeoJson(geoJson);
    }

    private JsonNode parseGeoJson(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(geoJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn("Stored verification geometry is not JSON: {}", exception.getMessage());
            return null;
        }
    }

    private final PlaceRepository placeRepository;
    private final PlaceSourceRepository placeSourceRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final FoodRepository foodRepository;
    private final PlaceReviewService placeReviewService;
    private final ImageMigrationService imageMigrationService;
    private final PlaceSearchIndexService placeSearchIndexService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final PlaceTranslationService placeTranslationService;
    private final PlaceSocialVideoService placeSocialVideoService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final LocationAreaService locationAreaService;
    private final com.ds.goroute.service.GeoService geoService;

    private static final Integer maxReview = 50;

    /** How far a same-named place may be from an imported one and still be that place. */
    private static final BigDecimal IMPORT_MATCH_RADIUS_METERS = BigDecimal.valueOf(15);

    /** GET /places page ceiling for guests and app users. */
    static final int PUBLIC_LIST_MAX_SIZE = 50;

    /** GET /places page ceiling for an admin (the console's linkable-place picker asks for 500). */
    static final int ADMIN_LIST_MAX_SIZE = 500;

    /** Shorter type-ahead text matches too much of the catalogue to be useful. */
    static final int MIN_SEARCH_LENGTH = 2;

    /*
     * No per-call cache eviction: import jobs call this once per place and evict the
     * place search cache once when they finish (evictSearchCache); a one-off import shows
     * up in cached searches within the cache's 2-minute TTL.
     */
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
                        log.error("Error migrating thumbnail: {}", e.getMessage(), e);
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
                        log.error("Error migrating place images: {}", e.getMessage(), e);
                    }
                }

                // Migrate menu photos stored inside the menu JSON object.
                if (request.getMenu() != null && !request.getMenu().trim().isEmpty()) {
                    try {
                        request.setMenu(imageMigrationService.migrateMenuJson(request.getMenu(), targetPath));
                    } catch (Exception e) {
                        log.error("Error migrating menu images: {}", e.getMessage(), e);
                    }
                }

                // NOTE: Review images will be migrated by PlaceReviewService.batchInsertReviews()
                // No need to migrate here to avoid double migration
            } else {
                log.warn("ImageMigrationService not available, skipping image migration");
            }

            // Check if place already exists
            Place existingPlace = findExistingPlaceForImport(request);
            if (existingPlace != null) {
                log.info("Place already exists, updating: {}", request.getPlaceId());
                if ("PARTNER".equals(placeSourceRepository.findPrimarySourceType(existingPlace.getId()))) {
                    placeSourceRepository.attachGoogleIdentity(existingPlace.getId(), request.getPlaceId(),
                            request.getCid(), request.getDataId(), request.getGoogleMapsLink(), LocalDateTime.now());
                    syncGoogleSource(existingPlace.getId(), request);
                    Place canonical = placeRepository.findById(existingPlace.getId()).orElse(existingPlace);
                    placeSearchIndexService.indexPlace(canonical);
                    return toPlaceResponse(canonical);
                }
                return updateExistingPlace(existingPlace, request);
            }

            // Create new place
            Place place = buildPlaceFromRequest(request);
            placeRepository.insert(place);
            // Ward first: the tourist-area resolver prefers a ward match over a radius.
            geoService.assignPlaceWard(place.getId());
            // New rows only: a re-import keeps whatever area the place already has.
            locationAreaService.assignNewPlace(place.getId());
            syncGoogleSource(place.getId(), request);
            placeTranslationService.syncTranslations(place, request.getTranslations());
            placeSearchIndexService.indexPlace(place);

            // Import reviews using PlaceReviewService for smart duplicate handling
            if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
                importReviewsViaService(request.getUserReviews());
            }

            return toPlaceResponse(place);

        } catch (Exception e) {
            log.error("Failed to import place {}: {}", request.getPlaceId(), e.getMessage(), e);
            return null; // Skip this place on error
        }
    }

    @CacheEvict(cacheNames = {"placeSearch", PlaceMapTileService.CACHE}, cacheManager = "searchCacheManager", allEntries = true)
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
        PlaceResponse response = toPlaceResponse(place);
        response.setSocialVideos(placeSocialVideoService.findByPlaceId(place.getId()));
        return response;
    }

    @Override
    public PlaceResponse getPlaceByGoogleId(String placeId) {
        Place place = placeRepository.findByPlaceId(placeId);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        PlaceResponse response = toPlaceResponse(place);
        response.setSocialVideos(placeSocialVideoService.findByPlaceId(place.getId()));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceResponse> getAllPlaces(String search, List<String> placeGroups, boolean includeInactive,
                                            int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, includeInactive ? ADMIN_LIST_MAX_SIZE : PUBLIC_LIST_MAX_SIZE));
        boolean publicOnly = !includeInactive;
        List<String> groups = normalizedPlaceGroups(placeGroups);
        String text = blankToNull(search);
        if (text == null) {
            return toListResponses(placeRepository.findListPage(groups, publicOnly, safeSize, safePage * safeSize));
        }
        if (text.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        UUID exactId = parseUuidOrNull(text);
        if (exactId != null) {
            return safePage > 0 ? List.of()
                    : toListResponses(inGroups(placeRepository.findListSummaryByIds(List.of(exactId), publicOnly), groups));
        }
        if (safePage == 0 && text.chars().noneMatch(Character::isWhitespace)) {
            Place byGoogleId = placeRepository.findByPlaceId(text);
            if (byGoogleId != null) {
                List<Place> exact = inGroups(
                        placeRepository.findListSummaryByIds(List.of(byGoogleId.getId()), publicOnly), groups);
                if (!exact.isEmpty()) {
                    return toListResponses(exact);
                }
            }
        }
        try {
            List<UUID> orderedIds = placeSearchIndexService.searchPlaceIdsByName(
                    text, groups, includeInactive, safePage, safeSize);
            return toListResponses(inIdOrder(orderedIds, placeRepository.findListSummaryByIds(orderedIds, publicOnly)));
        } catch (java.io.IOException e) {
            log.error("Place name search failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed");
        }
    }

    private static UUID parseUuidOrNull(String value) {
        if (value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    private static List<Place> inGroups(List<Place> places, List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return places;
        }
        Set<String> wanted = groups.stream().map(group -> group.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return places.stream()
                .filter(place -> place.getPlaceGroup() != null && wanted.contains(place.getPlaceGroup().name()))
                .toList();
    }

    /** The rows in the order the search returned their ids; ids without a row are skipped. */
    private static List<Place> inIdOrder(List<UUID> orderedIds, List<Place> rows) {
        Map<UUID, Place> byId = rows.stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (left, right) -> left));
        List<Place> ordered = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            Place place = byId.get(id);
            if (place != null) {
                ordered.add(place);
            }
        }
        return ordered;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDetailRefreshCandidates(UUID placeId, boolean includeInactive, Integer maxPlaces) {
        int limit = maxPlaces == null || maxPlaces <= 0
                ? DETAIL_REFRESH_CANDIDATE_LIMIT
                : Math.min(maxPlaces, DETAIL_REFRESH_CANDIDATE_LIMIT);
        List<PlaceDetailRefreshCandidateResponse> items =
                placeRepository.findDetailRefreshCandidates(placeId, includeInactive, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseCount", placeRepository.countAll());
        result.put("eligibleCount", items.size());
        result.put("includeInactive", includeInactive);
        result.put("items", items);
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Legacy group codes from older clients fold into ATTRACTIONS (see PlaceGroup.fromCode). */
    private static List<String> normalizedPlaceGroups(List<String> placeGroups) {
        return com.ds.goroute.type.PlaceGroup.normalizeCodes(placeGroups);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPlacePageResponse getAdminPlaces(String search, List<String> placeGroups, List<String> subTypes,
                                                 List<String> visibilityStatus,
                                                 List<String> trustLevel, List<UUID> locationImageIds,
                                                 String sort, boolean descending, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedSearch = blankToNull(search);
        UUID searchId = normalizedSearch == null ? null : parseUuidOrNull(normalizedSearch);
        List<String> normalizedGroups = normalizedPlaceGroups(placeGroups);
        List<String> normalizedSubTypes = AdminListSort.codes(subTypes);
        List<String> normalizedVisibility = AdminListSort.codes(visibilityStatus);
        List<String> normalizedTrust = AdminListSort.codes(trustLevel);
        List<UUID> normalizedAreas = AdminListSort.ids(locationImageIds);
        List<Place> places = placeRepository
                .findFilteredPage(normalizedSearch, searchId, normalizedGroups, normalizedSubTypes, normalizedVisibility, normalizedTrust, normalizedAreas,
                        sort, descending, safeSize, safePage * safeSize);
        Map<UUID, List<PlaceTranslation>> translations = placeTranslationService.findByPlaceIds(
                places.stream().map(Place::getId).toList());
        List<AdminPlaceResponse> items = places.stream()
                .map(place -> toAdminPlaceResponse(place, translations.getOrDefault(place.getId(), List.of())))
                .collect(Collectors.toList());
        return AdminPlacePageResponse.builder()
                .items(items)
                .total(placeRepository.countFiltered(normalizedSearch, searchId, normalizedGroups, normalizedSubTypes, normalizedVisibility, normalizedTrust, normalizedAreas))
                .page(safePage)
                .size(safeSize)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public AdminPlaceResponse getAdminPlaceById(UUID id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        return toAdminPlaceResponse(place);
    }

    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radius, String category, List<String> placeGroups,
                                            BigDecimal minRating, int page, int size) {
        return searchPlaces(keyword, latitude, longitude, radius, category, placeGroups, null,
                minRating, PlaceSearchSort.RELEVANCE, null, null, null, false, null, page, size);
    }

    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radius, String category, List<String> placeGroups,
                                            BigDecimal minRating, String citySlug, List<UUID> foodIds,
                                            Boolean excludeLinkedFoodPlaces, boolean includeInactive, int page, int size) {
        return searchPlaces(keyword, latitude, longitude, radius, category, placeGroups, null,
                minRating, PlaceSearchSort.RELEVANCE, citySlug, foodIds, excludeLinkedFoodPlaces, includeInactive,
                null, page, size);
    }

    @Cacheable(
            cacheNames = "placeSearch",
            cacheManager = "searchCacheManager",
            keyGenerator = "placeSearchCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()")
    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radius, String category, List<String> placeGroups,
                                            List<String> subTypes,
                                            BigDecimal minRating, PlaceSearchSort sort, String citySlug, List<UUID> foodIds,
                                            Boolean excludeLinkedFoodPlaces, boolean includeInactive,
                                            Float minLuceneScore, int page, int size) {
        try {
            PlaceSearchCriteria criteria = new PlaceSearchCriteria(
                    keyword, latitude, longitude, radius, category, normalizedPlaceGroups(placeGroups), subTypes, minRating,
                    citySlug, foodIds, excludeLinkedFoodPlaces, includeInactive, sort,
                    minLuceneScore, page, size);
            List<UUID> orderedIds = placeSearchIndexService.searchPlaceIds(criteria);
            if (orderedIds.isEmpty()) {
                return List.of();
            }

            // List columns only: the detail screen reloads the place by id for the heavy ones.
            List<Place> places = inIdOrder(orderedIds,
                    placeRepository.findListSummaryByIds(orderedIds, !includeInactive));
            for (Place place : places) {
                place.setDistance(GeoDistance.kilometresOrFarAway(
                        latitude, longitude, place.getLatitude(), place.getLongitude()));
            }

            List<PlaceResponse> responses = toListResponses(places);
            attachFoodTags(responses);
            return responses;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Place Lucene search failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed");
        }
    }

    @Override
    public Map<String, Integer> countPlacesBySubType(BigDecimal latitude, BigDecimal longitude, BigDecimal radius,
                                                    List<String> placeGroups, boolean includeInactive) {
        List<String> groups = normalizedPlaceGroups(placeGroups);
        List<PlaceSubType> kinds = PlaceSubType.fittingAny(groups == null ? null : groups.stream()
                .map(com.ds.goroute.type.PlaceGroup::fromCode)
                .filter(Objects::nonNull)
                .toList());
        try {
            PlaceSearchCriteria area = new PlaceSearchCriteria(
                    null, latitude, longitude, radius, null, groups, null, null,
                    null, null, null, includeInactive, PlaceSearchSort.RELEVANCE, null, 0, 1);
            return placeSearchIndexService.countBySubType(area, kinds);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Place sub-type count failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceResponse> getTrendingPlaces(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                                 List<String> placeGroups, PlaceTrendingPeriod period, int size) {
        List<UUID> orderedIds = placeRepository.findTrendingUsage(
                        latitude, longitude, radiusKm.multiply(BigDecimal.valueOf(1000)),
                        normalizedPlaceGroups(placeGroups), period.getDays(), size).stream()
                .map(PlaceTrendingRow::getPlaceId)
                .toList();
        if (orderedIds.isEmpty()) {
            return new ArrayList<>();
        }
        return toListResponses(inIdOrder(orderedIds, placeRepository.findListSummaryByIds(orderedIds, true)));
    }

    @Override
    public boolean triggerSearchReindex() {
        return placeSearchIndexService.triggerReindex();
    }

    @Override
    public void reindexPlace(UUID placeId) {
        placeRepository.findById(placeId).ifPresent(placeSearchIndexService::indexPlace);
    }

    @CacheEvict(cacheNames = {"placeSearch", PlaceMapTileService.CACHE}, cacheManager = "searchCacheManager", allEntries = true)
    @Override
    public void evictSearchCache() {
        log.debug("Place search cache cleared");
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
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }

        int offset = Math.max(0, page) * Math.max(1, size);
        
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

    @CacheEvict(cacheNames = {"placeSearch", PlaceMapTileService.CACHE}, cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public void deletePlace(UUID id) {
        if (!placeRepository.existsById(id)) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }

        imageStorageCleanupService.deleteImagesForEntityRecord("PLACE", id);
        // Delete reviews first
        placeReviewRepository.deleteByPlaceId(id);

        // Delete place
        placeRepository.delete(id);
        placeSearchIndexService.deletePlace(id);
        log.info("Deleted place: {}", id);
    }

    @CacheEvict(cacheNames = {"placeSearch", PlaceMapTileService.CACHE}, cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public PlaceResponse updatePlace(UUID id, UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        PlaceVisibilityStatus previousVisibility = place.getVisibilityStatus();
        boolean coordinatesChanged = !sameCoordinate(place.getLatitude(), request.getLatitude())
                || !sameCoordinate(place.getLongitude(), request.getLongitude());

        // Update all fields
        place.setTitle(request.getTitle());
        place.setCategory(request.getCategory());
        place.setPlaceGroup(com.ds.goroute.type.PlaceGroup.fromCode(request.getPlaceGroup()));
        String[] previousSubTypes = place.getSubTypes();
        place.setSubTypes(subTypesForUpdate(request.getSubTypes(), previousSubTypes, place.getPlaceGroup()));
        place.setAddress(request.getAddress());
        place.setDestinations(toJson(request.getDestinations()));
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setVerificationRadiusMeters(request.getVerificationRadiusMeters());
        place.setPlusCode(request.getPlusCode());
        place.setTimezone(preferNonBlank(request.getTimezone(), place.getTimezone()));
        place.setPhone(request.getPhone());
        place.setWebsite(request.getWebsite());
        place.setGoogleMapsLink(request.getGoogleMapsLink());
        place.setReviewCount(request.getReviewCount());
        place.setReviewRating(request.getReviewRating());
        place.setReviewsPerRating(request.getReviewsPerRating());
        place.setThumbnail(request.getThumbnail());
        place.setImages(request.getImages());
        place.setDescriptions(request.getDescriptions());
        place.setAiDescription(request.getAiDescription());
        if (request.getAiReferences() != null) {
            place.setAiReferences(serializeAiReferences(request.getAiReferences()));
        }
        place.setStatus(request.getStatus());
        place.setVisibilityStatus(parseVisibilityStatus(request.getVisibilityStatus(), place.getVisibilityStatus()));
        place.setPriceRange(request.getPriceRange());
        place.setOpenHours(request.getOpenHours());
        place.setRegular(request.getRegular());
        place.setPopularTimes(request.getPopularTimes());
        place.setReservations(request.getReservations());
        place.setOrderOnline(request.getOrderOnline());
        place.setMenu(request.getMenu());
        place.setCompleteAddress(request.getCompleteAddress());
        place.setAbout(request.getAbout());
        place.setOwner(request.getOwner());
        place.setEmails(request.getEmails());
        // Absent keeps the stored attributes; an explicit JSON null arrives as NullNode and clears them.
        if (request.getAttributes() != null) {
            place.setAttributes(serializeAttributes(request.getAttributes()));
        }
        place.setUpdatedAt(LocalDateTime.now());

        placeRepository.update(place);
        if (!Arrays.equals(previousSubTypes, place.getSubTypes())) {
            placeRepository.updateSubTypes(place.getId(), place.getSubTypes());
        }
        applyVerificationGeometry(place, request.getVerificationGeometry());
        if (request.getWardCode() != null) {
            String wardCode = request.getWardCode().isBlank() ? null : request.getWardCode().trim();
            placeRepository.updateWardCode(place.getId(), wardCode);
            place.setWardCode(wardCode);
        } else if (place.getWardCode() == null || coordinatesChanged) {
            reassignWard(place);
        }
        placeTranslationService.syncTranslations(place, request.getTranslations());
        placeSearchIndexService.indexPlace(place);
        if (previousVisibility != PlaceVisibilityStatus.ACTIVE
                && place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE) {
            eventPublisher.publishEvent(new PlaceActivatedEvent(place.getId()));
        }
        log.info("Updated place: {}", id);

        return toPlaceResponse(place);
    }

    // Helper methods

    private static boolean sameCoordinate(BigDecimal before, BigDecimal after) {
        return before == null ? after == null : after != null && before.compareTo(after) == 0;
    }

    /**
     * Ward and province from the place's current point. A point outside every loaded
     * boundary clears the ward rather than keeping the one the old point was in.
     */
    private void reassignWard(Place place) {
        if (placeRepository.assignWard(place.getId()) == 0 && place.getWardCode() != null) {
            placeRepository.updateWardCode(place.getId(), null);
        }
        placeRepository.findGeoCodes(place.getId()).ifPresent(codes -> {
            place.setWardCode(codes.getWardCode());
            place.setProvinceCode(codes.getProvinceCode());
        });
    }

    private Place findExistingPlaceForImport(ImportPlaceRequest request) {
        return PlaceIdentityMatcher.findExisting(placeRepository,
                        request.getPlaceId(), request.getCid(),
                        request.getLatitude(), request.getLongitude(),
                        request.getTitle(), request.getAddress(),
                        IMPORT_MATCH_RADIUS_METERS)
                .orElse(null);
    }

    private PlaceResponse updateExistingPlace(Place existingPlace, ImportPlaceRequest request) {
        Place updated = buildPlaceFromRequest(request);
        updated.setId(existingPlace.getId());
        updated.setCreatedAt(existingPlace.getCreatedAt());
        updated.setPlaceGroup(existingPlace.getPlaceGroup() != null
                ? existingPlace.getPlaceGroup()
                : updated.getPlaceGroup());
        // A refresh fills sub-types only while the place has none, so an operator's choice stays.
        boolean fillSubTypes = isEmpty(existingPlace.getSubTypes());
        updated.setSubTypes(fillSubTypes
                ? PlaceSubType.toStorable(request.getSubTypes(), updated.getPlaceGroup())
                : existingPlace.getSubTypes());
        updated.setCategory(existingPlace.getCategory() != null ? existingPlace.getCategory() : request.getCategory());
        // Imports do not carry the admin-only verification override; never erase it.
        updated.setVerificationRadiusMeters(existingPlace.getVerificationRadiusMeters());
        updated.setDescriptions(existingPlace.getDescriptions() != null ? existingPlace.getDescriptions() : request.getDescriptions());
        updated.setAddress(preferNonBlank(updated.getAddress(), existingPlace.getAddress()));
        updated.setDestinations(preferNonEmptyJson(updated.getDestinations(), existingPlace.getDestinations()));
        updated.setPlusCode(preferNonBlank(updated.getPlusCode(), existingPlace.getPlusCode()));
        updated.setTimezone(preferNonBlank(updated.getTimezone(), existingPlace.getTimezone()));
        updated.setPhone(preferNonBlank(updated.getPhone(), existingPlace.getPhone()));
        updated.setWebsite(preferNonBlank(updated.getWebsite(), existingPlace.getWebsite()));
        updated.setGoogleMapsLink(preferNonBlank(updated.getGoogleMapsLink(), existingPlace.getGoogleMapsLink()));
        updated.setReviewCount(updated.getReviewCount() != null && updated.getReviewCount() > 0
                ? updated.getReviewCount()
                : existingPlace.getReviewCount());
        updated.setReviewRating(updated.getReviewRating() != null && updated.getReviewRating().signum() > 0
                ? updated.getReviewRating()
                : existingPlace.getReviewRating());
        updated.setReviewsPerRating(preferNonEmptyJson(updated.getReviewsPerRating(), existingPlace.getReviewsPerRating()));
        updated.setThumbnail(preferNonBlank(updated.getThumbnail(), existingPlace.getThumbnail()));
        updated.setImages(preferNonEmptyJson(updated.getImages(), existingPlace.getImages()));
        updated.setAiDescription(existingPlace.getAiDescription());
        updated.setAiReferences(preferNonEmptyJson(updated.getAiReferences(), existingPlace.getAiReferences()));
        updated.setStatus(preferNonBlank(updated.getStatus(), existingPlace.getStatus()));
        updated.setPriceRange(preferNonBlank(updated.getPriceRange(), existingPlace.getPriceRange()));
        updated.setOpenHours(preferNonEmptyJson(updated.getOpenHours(), existingPlace.getOpenHours()));
        updated.setRegular(preferNonEmptyJson(updated.getRegular(), existingPlace.getRegular()));
        updated.setVisitDurationMinutes(existingPlace.getVisitDurationMinutes());
        updated.setPopularTimes(preferNonEmptyJson(updated.getPopularTimes(), existingPlace.getPopularTimes()));
        updated.setReservations(preferNonEmptyJson(updated.getReservations(), existingPlace.getReservations()));
        updated.setOrderOnline(preferNonEmptyJson(updated.getOrderOnline(), existingPlace.getOrderOnline()));
        updated.setMenu(preferNonEmptyJson(updated.getMenu(), existingPlace.getMenu()));
        updated.setCompleteAddress(preferNonEmptyJson(updated.getCompleteAddress(), existingPlace.getCompleteAddress()));
        updated.setAbout(preferNonEmptyJson(updated.getAbout(), existingPlace.getAbout()));
        updated.setOwner(preferNonEmptyJson(updated.getOwner(), existingPlace.getOwner()));
        updated.setEmails(preferNonEmptyJson(updated.getEmails(), existingPlace.getEmails()));
        updated.setAttributes(preferNonEmptyJson(updated.getAttributes(), existingPlace.getAttributes()));
        updated.setRawData(preferNonEmptyJson(updated.getRawData(), existingPlace.getRawData()));
        updated.setAvgAuthenticityScore(existingPlace.getAvgAuthenticityScore());
        updated.setPlaceOverallScore(existingPlace.getPlaceOverallScore());
        updated.setAdjustedRating(existingPlace.getAdjustedRating());
        updated.setTrustLevel(existingPlace.getTrustLevel());
        updated.setIsJcurveDetected(existingPlace.getIsJcurveDetected());
        updated.setIsSpikeDetected(existingPlace.getIsSpikeDetected());
        updated.setAuthenticLowStarCount(existingPlace.getAuthenticLowStarCount());
        updated.setScoreCalculatedAt(existingPlace.getScoreCalculatedAt());
        updated.setScoreSampleCount(existingPlace.getScoreSampleCount());
        updated.setScoreSource(existingPlace.getScoreSource());
        updated.setLastScrapedAt(LocalDateTime.now());  // Update to current time on refresh
        updated.setVisibilityStatus(parseVisibilityStatus(request.getVisibilityStatus(), existingPlace.getVisibilityStatus()));
        updated.setUpdatedAt(LocalDateTime.now());

        placeRepository.update(updated);
        if (fillSubTypes && !isEmpty(updated.getSubTypes())) {
            placeRepository.updateSubTypes(updated.getId(), updated.getSubTypes());
        }
        syncGoogleSource(updated.getId(), request);
        placeTranslationService.syncTranslations(updated, request.getTranslations());
        placeSearchIndexService.indexPlace(updated);

        // Update reviews using PlaceReviewService for smart duplicate handling
        if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
            importReviewsViaService(request.getUserReviews());
        }

        return toPlaceResponse(updated);
    }

    private void syncGoogleSource(UUID canonicalPlaceId, ImportPlaceRequest request) {
        if (request.getPlaceId() == null || request.getPlaceId().isBlank()) return;
        try {
            String payload = objectMapper.writeValueAsString(request);
            String checksum = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
            LocalDateTime observedAt = LocalDateTime.now();
            UUID sourceId = placeSourceRepository.upsertGoogleSource(UUID.randomUUID(), canonicalPlaceId,
                    request.getPlaceId(), request.getGoogleMapsLink(), request.getCid(), request.getDataId(),
                    checksum, observedAt);
            placeSourceRepository.insertSnapshot(UUID.randomUUID(), sourceId, payload, checksum, observedAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot persist Google place source snapshot", ex);
        }
    }

    private static boolean isEmpty(String[] values) {
        return values == null || values.length == 0;
    }

    /**
     * Sub-types after an operator edit. Null in the request keeps what is stored, minus kinds the
     * (possibly changed) group no longer allows; an explicit list must fit the group or the edit fails.
     */
    private static String[] subTypesForUpdate(List<PlaceSubType> requested, String[] stored,
                                              com.ds.goroute.type.PlaceGroup placeGroup) {
        if (requested == null) {
            return PlaceSubType.toStorable(stored == null ? List.of() : List.of(stored), placeGroup);
        }
        List<String> misfits = requested.stream()
                .filter(java.util.Objects::nonNull)
                .filter(kind -> !kind.fits(placeGroup))
                .map(Enum::name)
                .toList();
        if (!misfits.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Sub-types " + misfits + " do not fit place group " + placeGroup);
        }
        return PlaceSubType.toStorable(requested.stream()
                .filter(java.util.Objects::nonNull).map(Enum::name).toList(), placeGroup);
    }

    private String preferNonBlank(String incoming, String existing) {
        return incoming != null && !incoming.trim().isEmpty() ? incoming : existing;
    }

    private String preferNonEmptyJson(String incoming, String existing) {
        if (incoming == null) {
            return existing;
        }
        String normalized = incoming.trim();
        if (normalized.isEmpty()
                || normalized.equalsIgnoreCase("null")
                || normalized.equals("{}")
                || normalized.equals("[]")) {
            return existing != null ? existing : incoming;
        }
        return incoming;
    }

    private Place buildPlaceFromRequest(ImportPlaceRequest request) {
        com.ds.goroute.type.PlaceGroup placeGroup = com.ds.goroute.type.PlaceGroup.fromCode(request.getPlaceGroup());
        return Place.builder()
                .id(UUID.randomUUID())
                .placeId(request.getPlaceId())
                .cid(request.getCid())
                .dataId(request.getDataId())
                .title(request.getTitle())
                .category(request.getCategory())
                .placeGroup(placeGroup)
                .subTypes(PlaceSubType.toStorable(request.getSubTypes(), placeGroup))
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
                .aiDescription(request.getAiDescription())
                .aiReferences(serializeAiReferences(request.getAiReferences()))
                .status(request.getStatus())
                .visibilityStatus(parseVisibilityStatus(request.getVisibilityStatus(), PlaceVisibilityStatus.ACTIVE))
                .priceRange(request.getPriceRange())
                .openHours(request.getOpenHours())
                .regular(request.getRegular())
                .popularTimes(request.getPopularTimes())
                .reservations(request.getReservations())
                .orderOnline(request.getOrderOnline())
                .menu(request.getMenu())
                .completeAddress(request.getCompleteAddress())
                .about(request.getAbout())
                .owner(request.getOwner())
                .emails(request.getEmails())
                .attributes(serializeAttributes(request.getAttributes()))
                .rawData(request.getRawData())
                .lastScrapedAt(LocalDateTime.now())  // Set last_scraped_at on first import
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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
                    log.error("Error parsing review node: {}", e.getMessage(), e);
                }
            }

            // Call PlaceReviewService to handle batch insert with duplicate checking
            if (!reviewInputs.isEmpty()) {
                Map<String, Object> result = placeReviewService.batchInsertReviews(reviewInputs);
                log.info("Review import result: {}", result);
            }

        } catch (Exception e) {
            log.error("Error parsing reviews JSON: {}", e.getMessage(), e);
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

    private PlaceVisibilityStatus parseVisibilityStatus(String value, PlaceVisibilityStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback != null ? fallback : PlaceVisibilityStatus.ACTIVE;
        }
        try {
            return PlaceVisibilityStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid place visibilityStatus '{}', using fallback {}", value, fallback);
            return fallback != null ? fallback : PlaceVisibilityStatus.ACTIVE;
        }
    }

    /** Full single-place response: every translation of the place, one query for them. */
    private PlaceResponse toPlaceResponse(Place place) {
        return toPlaceResponse(place, placeTranslationService.findByPlaceIds(List.of(place.getId()))
                .getOrDefault(place.getId(), List.of()));
    }

    private PlaceResponse toPlaceResponse(Place place, List<PlaceTranslation> translations) {
        Map<String, PlaceTranslationResponse> all = new LinkedHashMap<>();
        translations.stream()
                .sorted(Comparator.comparing(translation -> translation.getLocale().code()))
                .forEach(translation -> all.put(translation.getLocale().code(),
                        PlaceTranslationService.responseOf(translation)));
        return buildPlaceResponse(place,
                PlaceTranslationService.resolveFrom(translations, AcceptLanguageFilter.currentCode()), all);
    }

    /**
     * List responses: translations loaded once for the whole page, and only the resolved
     * locale sent back. The places come from the list projection, so the heavy JSON fields
     * (menu, popular times, about, ratings histogram, AI references, attributes) stay null.
     */
    private List<PlaceResponse> toListResponses(List<Place> places) {
        if (places.isEmpty()) {
            return new ArrayList<>();
        }
        Map<UUID, List<PlaceTranslation>> translations = placeTranslationService.findByPlaceIds(
                places.stream().map(Place::getId).toList());
        String locale = AcceptLanguageFilter.currentCode();
        List<PlaceResponse> responses = new ArrayList<>(places.size());
        for (Place place : places) {
            PlaceTranslation translation = PlaceTranslationService.resolveFrom(
                    translations.getOrDefault(place.getId(), List.of()), locale);
            Map<String, PlaceTranslationResponse> current = new LinkedHashMap<>();
            if (translation != null) {
                current.put(translation.getLocale().code(), PlaceTranslationService.responseOf(translation));
            }
            PlaceResponse response = buildPlaceResponse(place, translation, current);
            response.setAttributes(null);
            responses.add(response);
        }
        return responses;
    }

    private PlaceResponse buildPlaceResponse(Place place, PlaceTranslation translation,
                                             Map<String, PlaceTranslationResponse> translationResponses) {
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
                .translations(translationResponses)
                .category(place.getCategory())
                .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : null)
                .subTypes(place.getSubTypes() != null ? List.of(place.getSubTypes()) : List.of())
                .address(place.getAddress())
                .destinations(parseJsonToStringList(place.getDestinations()))
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .verificationRadiusMeters(place.getVerificationRadiusMeters())
                .verificationGeometry(parseGeoJson(place.getVerificationGeometryGeoJson()))
                .wardCode(place.getWardCode())
                .provinceCode(place.getProvinceCode())
                .phone(place.getPhone())
                .website(place.getWebsite())
                .googleMapsLink(place.getGoogleMapsLink())
                .reviewCount(place.getReviewCount())
                .tripUseCount(place.getTripUseCount())
                .checkinCount(place.getCheckinCount())
                .reviewRating(place.getReviewRating())
                .adjustedRating(PlaceDisplayRating.of(place))
                .placeOverallScore(place.getPlaceOverallScore())
                .scoreSampleCount(place.getScoreSampleCount())
                .scoreSource(place.getScoreSource())
                .lastScrapedAt(place.getLastScrapedAt())
                .reviewsPerRating(parseJsonToMap(place.getReviewsPerRating()))
                .thumbnail(place.getThumbnail())
                .images(parseJsonToList(place.getImages(), PlaceImagesDto.class))
                .menu(parseJsonToMenu(place.getMenu()))
                .descriptions(resolvedDescription)
                .aiDescription(place.getAiDescription())
                .aiReferences(parseJsonNode(place.getAiReferences()))
                .visibilityStatus(place.getVisibilityStatus() != null
                        ? place.getVisibilityStatus().name()
                        : PlaceVisibilityStatus.ACTIVE.name())
                .priceRange(place.getPriceRange())
                .openHours(parseJsonToMapOfList(place.getOpenHours()))
                .regular(parseJsonToMapOfList(place.getRegular()))
                .popularTimes(parseJsonToMapOfMap(place.getPopularTimes()))
                .about(parseJsonToList(place.getAbout(), PlaceAboutDto.class))
                .attributes(PlaceAttributeCatalog.publicAttributes(parseJsonNode(place.getAttributes())))
                .distance(place.getDistance())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }

    private PlaceMenuDto parseJsonToMenu(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);
            if (!rootNode.isObject()) {
                return null;
            }

            ObjectNode normalized = objectMapper.createObjectNode();
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                dataNode = rootNode.get("images");
            }
            normalized.set("data", normalizeMenuItems(dataNode, true));
            normalized.set("highlights", normalizeMenuItems(rootNode.get("highlights"), false));
            return objectMapper.treeToValue(normalized, PlaceMenuDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse menu JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    private ArrayNode normalizeMenuItems(JsonNode node, boolean requireUrl) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }

        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            String url = "";
            String title = "";
            if (item.isTextual()) {
                title = item.asText().trim();
            } else if (item.isObject()) {
                url = item.path("url").asText();
                if (url.isBlank()) {
                    url = item.path("image").asText();
                }
                title = item.path("title").asText();
                if (title.isBlank()) {
                    title = item.path("name").asText();
                }
            }

            if (requireUrl && url.isBlank()) {
                continue;
            }
            if (!requireUrl && title.equalsIgnoreCase("menu")) {
                continue;
            }
            if (url.isBlank() && title.isBlank()) {
                continue;
            }

            String key = url + "\u0000" + title;
            if (!seen.add(key)) {
                continue;
            }
            ObjectNode normalizedItem = objectMapper.createObjectNode();
            normalizedItem.put("url", url);
            normalizedItem.put("title", title);
            normalized.add(normalizedItem);
        }
        return normalized;
    }

    private AdminPlaceResponse toAdminPlaceResponse(Place place) {
        return toAdminPlaceResponse(place, placeTranslationService.findByPlaceIds(List.of(place.getId()))
                .getOrDefault(place.getId(), List.of()));
    }

    private AdminPlaceResponse toAdminPlaceResponse(Place place, List<PlaceTranslation> translations) {
        AdminPlaceResponse response = objectMapper.convertValue(toPlaceResponse(place, translations), AdminPlaceResponse.class);
        response.setLocationImageId(place.getLocationImageId());
        response.setCid(place.getCid());
        response.setDataId(place.getDataId());
        response.setInputId(place.getInputId());
        response.setPlusCode(place.getPlusCode());
        response.setTimezone(place.getTimezone());
        response.setVisitDurationMinutes(place.getVisitDurationMinutes());
        response.setStatus(place.getStatus());
        response.setReservations(place.getReservations());
        response.setOrderOnline(place.getOrderOnline());
        response.setCompleteAddress(place.getCompleteAddress());
        response.setOwner(place.getOwner());
        response.setEmails(place.getEmails());
        response.setRawData(place.getRawData());
        response.setTrustLevel(place.getTrustLevel() != null ? place.getTrustLevel().name() : null);
        response.setJcurveDetected(place.getIsJcurveDetected());
        response.setSpikeDetected(place.getIsSpikeDetected());
        response.setAuthenticLowStarCount(place.getAuthenticLowStarCount());
        response.setAvgAuthenticityScore(place.getAvgAuthenticityScore());
        // The app is sent the display rating in this field; operators still see the recalculated one.
        response.setAdjustedRating(place.getAdjustedRating());
        response.setScoreCalculatedAt(place.getScoreCalculatedAt());
        response.setAttributes(PlaceAttributeCatalog.adminAttributes(parseJsonNode(place.getAttributes())));
        return response;
    }

    private JsonNode parseJsonNode(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            return node == null || node.isNull() ? null : node;
        } catch (Exception e) {
            log.warn("Failed to parse place attributes JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    private String serializeAttributes(JsonNode attributes) {
        if (attributes == null || attributes.isNull()) {
            return null;
        }
        try {
            return PlaceAttributeCatalog.normalizeForStorage(attributes, objectMapper);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "attributes must be valid JSON");
        }
    }

    private String serializeAiReferences(JsonNode references) {
        if (references == null || references.isNull()) {
            return null;
        }
        if (!references.isArray()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "aiReferences must be a JSON array");
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (Exception e) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "aiReferences must be valid JSON");
        }
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
            log.warn("Failed to parse JSON to Map: {}", e.getMessage(), e);
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
            log.warn("Failed to parse JSON to List: {}", e.getMessage(), e);
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
            log.warn("Failed to parse destinations JSON: {}", e.getMessage(), e);
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
            log.warn("Failed to serialize value to JSON: {}", e.getMessage(), e);
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
            log.warn("Failed to parse JSON to Map<String, List<String>>: {}", e.getMessage(), e);
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
            log.warn("Failed to parse JSON to Map<String, Map<String, Integer>>: {}", e.getMessage(), e);
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
