package com.ds.goroute.repository;

import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodCityScore;
import com.ds.goroute.entity.FoodPlaceRow;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.entity.FoodWithScoreRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodRepository {

    void insertFood(Food food);

    void updateFood(Food food);
    
    void update(Food food); // Alias for updateFood

    void deleteFood(UUID id);

    Optional<Food> findFoodById(UUID id);
    
    List<Food> findAll();

    List<Food> findAllFoods(String q, int limit, int offset);

    long countAllFoods(String q);

    List<String> findKnownCitySlugs();

    List<FoodWithScoreRow> findFoodsByCity(String citySlug, int minScore, int limit, int offset);

    Optional<FoodWithScoreRow> findFoodDetailByCity(UUID foodId, String citySlug);

    void insertCityScore(FoodCityScore score);

    void updateCityScore(FoodCityScore score);

    void deleteCityScore(UUID id);

    Optional<FoodCityScore> findCityScoreById(UUID id);

    List<FoodCityScore> findCityScoresByFoodId(UUID foodId);

    void linkPlace(UUID placeId, UUID foodId);

    void unlinkPlace(UUID placeId, UUID foodId);

    long countLinkedPlaces(UUID foodId, String citySlugJson);

    List<FoodPlaceRow> findPlacesByFood(UUID foodId, String citySlugJson, BigDecimal lat, BigDecimal lng,
                                        boolean useDistance, int limit, int offset);

    long countPlacesByFood(UUID foodId, String citySlugJson);

    List<FoodPlaceRow> findLinkedPlacesAdmin(UUID foodId, String citySlugJson, int limit, int offset);

    List<FoodTagRow> findFoodTagsByPlaceIds(List<UUID> placeIds);

    List<FoodWithScoreRow> findFoodsByPlaceId(UUID placeId, String citySlug);
}
