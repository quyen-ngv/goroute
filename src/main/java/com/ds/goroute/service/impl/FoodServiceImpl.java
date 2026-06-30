package com.ds.goroute.service.impl;

import com.ds.goroute.constant.CitySlug;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodCityScore;
import com.ds.goroute.entity.FoodPlaceRow;
import com.ds.goroute.entity.FoodWithScoreRow;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.FoodNameResolver;
import com.ds.goroute.utils.FoodScoreLabelResolver;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.AddressDistrictParser;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodServiceImpl implements FoodService {

    private static final int MIN_SCORE_PUBLIC = 50;

    private final FoodRepository foodRepository;
    private final PlaceRepository placeRepository;

    @Override
    @Cacheable(
            cacheNames = "foodsByCity",
            cacheManager = "foodsByCityCacheManager",
            key = "#citySlug + ':' + #page + ':' + #limit",
            unless = "#result == null || #result.isEmpty()")
    public List<FoodSummaryResponse> listByCity(String citySlug, int limit, int page) {
        String normalizedCitySlug = CitySlugResolver.normalizeRequired(citySlug);
        int offset = page * limit;
        return foodRepository.findFoodsByCity(normalizedCitySlug, MIN_SCORE_PUBLIC, limit, offset).stream()
                .map(row -> toSummary(row, normalizedCitySlug))
                .collect(Collectors.toList());
    }

    @Override
    public FoodDetailResponse getDetail(UUID foodId, String citySlug) {
        String normalizedCitySlug = CitySlugResolver.normalizeRequired(citySlug);
        FoodWithScoreRow row = foodRepository.findFoodDetailByCity(foodId, normalizedCitySlug)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Food not found for city"));
        if (row.getScore() == null || row.getScore() < MIN_SCORE_PUBLIC) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Food not available for city");
        }
        return toDetail(row, normalizedCitySlug);
    }

    @Override
    public List<CitySlugOptionResponse> listCitiesForFood(UUID foodId) {
        foodRepository.findFoodById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Food not found"));
        return foodRepository.findCityScoresByFoodId(foodId).stream()
                .filter(score -> score.getScore() != null && score.getScore() >= MIN_SCORE_PUBLIC)
                .sorted(Comparator.comparing(FoodCityScore::getScore).reversed())
                .map(score -> CitySlugOptionResponse.builder()
                        .slug(score.getCitySlug())
                        .label(CitySlugResolver.displayName(score.getCitySlug()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public FoodPlacePageResponse listPlacesForFood(UUID foodId, String citySlug, BigDecimal lat, BigDecimal lng,
                                                   int page, int size) {
        String normalizedCitySlug = CitySlugResolver.normalizeRequired(citySlug);
        foodRepository.findFoodDetailByCity(foodId, normalizedCitySlug)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Food not found for city"));

        String cityJson = CitySlugResolver.toJsonbFilter(normalizedCitySlug);
        boolean useDistance = lat != null && lng != null;
        int offset = page * size;
        List<FoodPlaceRow> rows = foodRepository.findPlacesByFood(
                foodId, cityJson, lat, lng, useDistance, size, offset);
        long total = foodRepository.countPlacesByFood(foodId, cityJson);

        List<FoodPlaceItemResponse> items = rows.stream()
                .map(row -> toPlaceItem(row, useDistance))
                .collect(Collectors.toList());

        return FoodPlacePageResponse.builder()
                .items(items)
                .total(total)
                .locationEnabled(useDistance)
                .page(page)
                .size(size)
                .build();
    }

    @Override
    public List<FoodSummaryResponse> listFoodsForPlace(UUID placeId, String citySlug) {
        String normalizedCitySlug = CitySlugResolver.normalizeRequired(citySlug);
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        return foodRepository.findFoodsByPlaceId(placeId, normalizedCitySlug).stream()
                .map(row -> toPlaceFoodSummary(row, normalizedCitySlug))
                .collect(Collectors.toList());
    }

    @Override
    public List<CitySlugOptionResponse> listCitySlugOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        Arrays.stream(CitySlug.values()).forEach(c -> options.put(c.getSlug(), c.getDisplayName()));
        foodRepository.findKnownCitySlugs().forEach(slug -> {
            String normalized = CitySlugResolver.normalizeRequired(slug);
            options.putIfAbsent(normalized, CitySlugResolver.displayName(normalized));
        });
        return options.entrySet().stream()
                .map(entry -> CitySlugOptionResponse.builder().slug(entry.getKey()).label(entry.getValue()).build())
                .collect(Collectors.toList());
    }

    @Override
    public List<FoodTagResponse> adminListFoodTagsForPlace(UUID placeId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        return foodRepository.findFoodTagsByPlaceIds(List.of(placeId)).stream()
                .map(row -> {
                    Food food = Food.builder()
                            .nameVi(row.getNameVi())
                            .nameEn(row.getNameEn())
                            .nameJa(row.getNameJa())
                            .nameKo(row.getNameKo())
                            .build();
                    return FoodTagResponse.builder()
                            .id(row.getFoodId())
                            .name(FoodNameResolver.resolveName(food))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void adminLinkFoodToPlace(UUID placeId, UUID foodId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        ensureFoodExists(foodId);
        foodRepository.linkPlace(placeId, foodId);
    }

    @Override
    @Transactional
    public void adminUnlinkFoodFromPlace(UUID placeId, UUID foodId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        ensureFoodExists(foodId);
        foodRepository.unlinkPlace(placeId, foodId);
    }

    @Override
    public List<FoodSummaryResponse> adminListAll(String q, int page, int size) {
        int offset = page * size;
        return foodRepository.findAllFoods(q, size, offset).stream()
                .map(food -> FoodSummaryResponse.builder()
                        .id(food.getId())
                        .name(FoodNameResolver.resolveName(food))
                        .description(food.getDescription())
                        .category(food.getCategory())
                        .imageUrl(food.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public AdminFoodDetailResponse adminGetDetail(UUID foodId) {
        Food food = foodRepository.findFoodById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Food not found"));
        return toAdminDetail(food);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public AdminFoodDetailResponse adminCreate(CreateFoodRequest request) {
        Food food = Food.builder()
                .id(UUID.randomUUID())
                .nameVi(request.getNameVi())
                .nameEn(request.getNameEn())
                .nameJa(request.getNameJa())
                .nameKo(request.getNameKo())
                .description(request.getDescription())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .introductionImages(toIntroductionImagesJson(request.getIntroductionImages()))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        foodRepository.insertFood(food);
        return toAdminDetail(food);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public AdminFoodDetailResponse adminUpdate(UUID foodId, UpdateFoodRequest request) {
        Food food = foodRepository.findFoodById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Food not found"));
        if (request.getNameVi() != null) food.setNameVi(request.getNameVi());
        if (request.getNameEn() != null) food.setNameEn(request.getNameEn());
        if (request.getNameJa() != null) food.setNameJa(request.getNameJa());
        if (request.getNameKo() != null) food.setNameKo(request.getNameKo());
        if (request.getDescription() != null) food.setDescription(request.getDescription());
        if (request.getCategory() != null) food.setCategory(request.getCategory());
        if (request.getImageUrl() != null) food.setImageUrl(request.getImageUrl());
        if (request.getIntroductionImages() != null) {
            food.setIntroductionImages(toIntroductionImagesJson(request.getIntroductionImages()));
        }
        food.setUpdatedAt(OffsetDateTime.now());
        foodRepository.updateFood(food);
        return toAdminDetail(food);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public void adminDelete(UUID foodId) {
        if (foodRepository.findFoodById(foodId).isEmpty()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Food not found");
        }
        foodRepository.deleteFood(foodId);
    }

    @Override
    public List<FoodCityScoreResponse> adminListCityScores(UUID foodId) {
        ensureFoodExists(foodId);
        return foodRepository.findCityScoresByFoodId(foodId).stream()
                .map(this::toCityScoreResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public FoodCityScoreResponse adminCreateCityScore(UUID foodId, CreateFoodCityScoreRequest request) {
        ensureFoodExists(foodId);
        String normalizedCitySlug = CitySlugResolver.normalizeRequired(request.getCitySlug());
        FoodCityScore score = FoodCityScore.builder()
                .id(UUID.randomUUID())
                .foodId(foodId)
                .citySlug(normalizedCitySlug)
                .score(request.getScore())
                .localDescription(request.getLocalDescription())
                .imageUrl(blankToNull(request.getImageUrl()))
                .introductionImages(toIntroductionImagesJson(request.getIntroductionImages()))
                .flavorProfile(toJson(request.getFlavorProfile()))
                .funFact(request.getFunFact())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        foodRepository.insertCityScore(score);
        return toCityScoreResponse(score);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public FoodCityScoreResponse adminUpdateCityScore(UUID foodId, UUID scoreId, UpdateFoodCityScoreRequest request) {
        ensureFoodExists(foodId);
        FoodCityScore score = foodRepository.findCityScoreById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "City score not found"));
        if (!score.getFoodId().equals(foodId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Score does not belong to food");
        }
        if (request.getCitySlug() != null) {
            score.setCitySlug(CitySlugResolver.normalizeRequired(request.getCitySlug()));
        }
        if (request.getScore() != null) score.setScore(request.getScore());
        if (request.getLocalDescription() != null) score.setLocalDescription(request.getLocalDescription());
        if (request.getImageUrl() != null) score.setImageUrl(blankToNull(request.getImageUrl()));
        if (request.getIntroductionImages() != null) {
            score.setIntroductionImages(toIntroductionImagesJson(request.getIntroductionImages()));
        }
        if (request.getFlavorProfile() != null) score.setFlavorProfile(toJson(request.getFlavorProfile()));
        if (request.getFunFact() != null) score.setFunFact(request.getFunFact());
        score.setUpdatedAt(OffsetDateTime.now());
        foodRepository.updateCityScore(score);
        return toCityScoreResponse(score);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "foodsByCity", cacheManager = "foodsByCityCacheManager", allEntries = true)
    public void adminDeleteCityScore(UUID foodId, UUID scoreId) {
        ensureFoodExists(foodId);
        FoodCityScore score = foodRepository.findCityScoreById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "City score not found"));
        if (!score.getFoodId().equals(foodId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Score does not belong to food");
        }
        foodRepository.deleteCityScore(scoreId);
    }

    @Override
    public List<FoodPlaceItemResponse> adminListLinkedPlaces(UUID foodId, String citySlug, int page, int size) {
        ensureFoodExists(foodId);
        String cityJson = citySlug != null && !citySlug.isBlank()
                ? CitySlugResolver.toJsonbFilter(citySlug)
                : null;
        return foodRepository.findLinkedPlacesAdmin(foodId, cityJson, size, page * size).stream()
                .map(row -> toPlaceItem(row, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void adminLinkPlace(UUID foodId, LinkFoodPlaceRequest request) {
        ensureFoodExists(foodId);
        placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        foodRepository.linkPlace(request.getPlaceId(), foodId);
    }

    @Override
    @Transactional
    public void adminBatchLinkPlaces(UUID foodId, BatchLinkFoodPlacesRequest request) {
        ensureFoodExists(foodId);
        for (UUID placeId : request.getPlaceIds()) {
            if (placeRepository.findById(placeId).isPresent()) {
                foodRepository.linkPlace(placeId, foodId);
            }
        }
    }

    @Override
    @Transactional
    public void adminUnlinkPlace(UUID foodId, UUID placeId) {
        ensureFoodExists(foodId);
        foodRepository.unlinkPlace(placeId, foodId);
    }

    private void ensureFoodExists(UUID foodId) {
        if (foodRepository.findFoodById(foodId).isEmpty()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Food not found");
        }
    }

    private AdminFoodDetailResponse toAdminDetail(Food food) {
        List<FoodCityScoreResponse> scores = foodRepository.findCityScoresByFoodId(food.getId()).stream()
                .map(this::toCityScoreResponse)
                .collect(Collectors.toList());
        return AdminFoodDetailResponse.builder()
                .id(food.getId())
                .names(FoodNameResolver.allNames(food))
                .description(food.getDescription())
                .category(food.getCategory())
                .imageUrl(food.getImageUrl())
                .introductionImages(parseIntroductionImages(food.getIntroductionImages()))
                .cityScores(scores)
                .linkedPlacesCount(foodRepository.countLinkedPlaces(food.getId(), null))
                .createdAt(food.getCreatedAt())
                .updatedAt(food.getUpdatedAt())
                .build();
    }

    private FoodSummaryResponse toSummary(FoodWithScoreRow row, String citySlug) {
        Food food = toFood(row);
        String description = row.getLocalDescription() != null && !row.getLocalDescription().isBlank()
                ? row.getLocalDescription()
                : row.getDescription();
        return FoodSummaryResponse.builder()
                .id(row.getId())
                .name(FoodNameResolver.resolveName(food))
                .description(description)
                .score(row.getScore())
                .scoreLabelKey(FoodScoreLabelResolver.toLabelKey(row.getScore()))
                .imageUrl(row.getImageUrl())
                .category(row.getCategory())
                .citySlug(citySlug)
                .build();
    }

    private FoodSummaryResponse toPlaceFoodSummary(FoodWithScoreRow row, String citySlug) {
        Food food = toFood(row);
        String description = row.getLocalDescription() != null && !row.getLocalDescription().isBlank()
                ? row.getLocalDescription()
                : row.getDescription();
        Integer score = row.getScore();
        Integer publicScore = score != null && score >= MIN_SCORE_PUBLIC ? score : null;
        return FoodSummaryResponse.builder()
                .id(row.getId())
                .name(FoodNameResolver.resolveName(food))
                .description(description)
                .score(publicScore)
                .scoreLabelKey(FoodScoreLabelResolver.toLabelKey(score))
                .imageUrl(row.getImageUrl())
                .category(row.getCategory())
                .citySlug(citySlug)
                .build();
    }

    private FoodDetailResponse toDetail(FoodWithScoreRow row, String citySlug) {
        Food food = toFood(row);
        String localDescription = row.getLocalDescription() != null && !row.getLocalDescription().isBlank()
                ? row.getLocalDescription()
                : null;
        return FoodDetailResponse.builder()
                .id(row.getId())
                .name(FoodNameResolver.resolveName(food))
                .description(row.getDescription())
                .generalDescription(row.getDescription())
                .localDescription(localDescription)
                .score(row.getScore())
                .scoreLabelKey(FoodScoreLabelResolver.toLabelKey(row.getScore()))
                .category(row.getCategory())
                .imageUrl(row.getImageUrl())
                .introductionImages(parseIntroductionImages(row.getIntroductionImages()))
                .citySlug(citySlug)
                .cityDisplayName(CitySlugResolver.displayName(citySlug))
                .flavorProfile(parseFlavor(row.getFlavorProfile()))
                .funFact(row.getFunFact())
                .build();
    }

    private FoodPlaceItemResponse toPlaceItem(FoodPlaceRow row, boolean useDistance) {
        Integer distanceMeters = null;
        if (useDistance && row.getDistanceMeters() != null) {
            distanceMeters = (int) Math.round(row.getDistanceMeters());
        }
        return FoodPlaceItemResponse.builder()
                .id(row.getId())
                .title(row.getTitle())
                .thumbnail(row.getThumbnail())
                .adjustedRating(row.getAdjustedRating())
                .priceRange(row.getPriceRange())
                .address(row.getAddress())
                .district(AddressDistrictParser.extractDistrict(row.getAddress()))
                .distanceMeters(distanceMeters)
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .build();
    }

    private FoodCityScoreResponse toCityScoreResponse(FoodCityScore score) {
        return FoodCityScoreResponse.builder()
                .id(score.getId())
                .citySlug(score.getCitySlug())
                .score(score.getScore())
                .localDescription(score.getLocalDescription())
                .imageUrl(score.getImageUrl())
                .introductionImages(parseIntroductionImages(score.getIntroductionImages()))
                .flavorProfile(parseFlavor(score.getFlavorProfile()))
                .funFact(score.getFunFact())
                .build();
    }

    private Food toFood(FoodWithScoreRow row) {
        return Food.builder()
                .id(row.getId())
                .nameVi(row.getNameVi())
                .nameEn(row.getNameEn())
                .nameJa(row.getNameJa())
                .nameKo(row.getNameKo())
                .description(row.getDescription())
                .category(row.getCategory())
                .imageUrl(row.getImageUrl())
                .build();
    }

    private Map<String, Object> parseFlavor(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(map);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<PlaceImagesDto> parseIntroductionImages(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.fromJson(json, new TypeReference<List<PlaceImagesDto>>() {});
    }

    private String toIntroductionImagesJson(List<PlaceImagesDto> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return JsonUtils.toJson(images);
    }
}
