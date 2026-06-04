package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodCityScore;
import com.ds.goroute.entity.FoodPlaceRow;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.entity.FoodWithScoreRow;
import com.ds.goroute.mapper.FoodMapper;
import com.ds.goroute.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FoodRepositoryImpl implements FoodRepository {

    private final FoodMapper foodMapper;

    @Override
    public void insertFood(Food food) {
        foodMapper.insertFood(food);
    }

    @Override
    public void updateFood(Food food) {
        foodMapper.updateFood(food);
    }

    @Override
    public void deleteFood(UUID id) {
        foodMapper.deleteFood(id);
    }

    @Override
    public Optional<Food> findFoodById(UUID id) {
        return Optional.ofNullable(foodMapper.findFoodById(id));
    }

    @Override
    public List<Food> findAllFoods(String q, int limit, int offset) {
        return foodMapper.findAllFoods(q, limit, offset);
    }

    @Override
    public long countAllFoods(String q) {
        return foodMapper.countAllFoods(q);
    }

    @Override
    public List<String> findKnownCitySlugs() {
        return foodMapper.findKnownCitySlugs();
    }

    @Override
    public List<FoodWithScoreRow> findFoodsByCity(String citySlug, int minScore, int limit, int offset) {
        return foodMapper.findFoodsByCity(citySlug, minScore, limit, offset);
    }

    @Override
    public Optional<FoodWithScoreRow> findFoodDetailByCity(UUID foodId, String citySlug) {
        return Optional.ofNullable(foodMapper.findFoodDetailByCity(foodId, citySlug));
    }

    @Override
    public void insertCityScore(FoodCityScore score) {
        foodMapper.insertCityScore(score);
    }

    @Override
    public void updateCityScore(FoodCityScore score) {
        foodMapper.updateCityScore(score);
    }

    @Override
    public void deleteCityScore(UUID id) {
        foodMapper.deleteCityScore(id);
    }

    @Override
    public Optional<FoodCityScore> findCityScoreById(UUID id) {
        return Optional.ofNullable(foodMapper.findCityScoreById(id));
    }

    @Override
    public List<FoodCityScore> findCityScoresByFoodId(UUID foodId) {
        return foodMapper.findCityScoresByFoodId(foodId);
    }

    @Override
    public void linkPlace(UUID placeId, UUID foodId) {
        foodMapper.insertPlaceFood(placeId, foodId);
    }

    @Override
    public void unlinkPlace(UUID placeId, UUID foodId) {
        foodMapper.deletePlaceFood(placeId, foodId);
    }

    @Override
    public long countLinkedPlaces(UUID foodId, String citySlugJson) {
        return foodMapper.countLinkedPlaces(foodId, citySlugJson);
    }

    @Override
    public List<FoodPlaceRow> findPlacesByFood(UUID foodId, String citySlugJson, BigDecimal lat, BigDecimal lng,
                                               boolean useDistance, int limit, int offset) {
        return foodMapper.findPlacesByFood(foodId, citySlugJson, lat, lng, useDistance, limit, offset);
    }

    @Override
    public long countPlacesByFood(UUID foodId, String citySlugJson) {
        return foodMapper.countPlacesByFood(foodId, citySlugJson);
    }

    @Override
    public List<FoodPlaceRow> findLinkedPlacesAdmin(UUID foodId, String citySlugJson, int limit, int offset) {
        return foodMapper.findLinkedPlacesAdmin(foodId, citySlugJson, limit, offset);
    }

    @Override
    public List<FoodTagRow> findFoodTagsByPlaceIds(List<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        return foodMapper.findFoodTagsByPlaceIds(placeIds);
    }

    @Override
    public List<FoodWithScoreRow> findFoodsByPlaceId(UUID placeId, String citySlug) {
        return foodMapper.findFoodsByPlaceId(placeId, citySlug);
    }
}
