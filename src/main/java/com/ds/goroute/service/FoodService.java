package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FoodService {

    List<FoodSummaryResponse> listByCity(String citySlug, int limit, int page);

    FoodDetailResponse getDetail(UUID foodId, String citySlug);

    FoodPlacePageResponse listPlacesForFood(UUID foodId, String citySlug, BigDecimal lat, BigDecimal lng, int page, int size);

    List<FoodSummaryResponse> listFoodsForPlace(UUID placeId, String citySlug);

    List<CitySlugOptionResponse> listCitySlugOptions();

    List<FoodTagResponse> adminListFoodTagsForPlace(UUID placeId);

    void adminLinkFoodToPlace(UUID placeId, UUID foodId);

    void adminUnlinkFoodFromPlace(UUID placeId, UUID foodId);

    // Admin
    List<FoodSummaryResponse> adminListAll(String q, int page, int size);

    AdminFoodDetailResponse adminGetDetail(UUID foodId);

    AdminFoodDetailResponse adminCreate(CreateFoodRequest request);

    AdminFoodDetailResponse adminUpdate(UUID foodId, UpdateFoodRequest request);

    void adminDelete(UUID foodId);

    List<FoodCityScoreResponse> adminListCityScores(UUID foodId);

    FoodCityScoreResponse adminCreateCityScore(UUID foodId, CreateFoodCityScoreRequest request);

    FoodCityScoreResponse adminUpdateCityScore(UUID foodId, UUID scoreId, UpdateFoodCityScoreRequest request);

    void adminDeleteCityScore(UUID foodId, UUID scoreId);

    List<FoodPlaceItemResponse> adminListLinkedPlaces(UUID foodId, String citySlug, int page, int size);

    void adminLinkPlace(UUID foodId, LinkFoodPlaceRequest request);

    void adminBatchLinkPlaces(UUID foodId, BatchLinkFoodPlacesRequest request);

    void adminUnlinkPlace(UUID foodId, UUID placeId);
}
