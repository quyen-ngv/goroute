package com.ds.goroute.mapper;

import com.ds.goroute.entity.Food;
import com.ds.goroute.entity.FoodCityScore;
import com.ds.goroute.entity.FoodPlaceRow;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.entity.FoodWithScoreRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface FoodMapper {

    void insertFood(Food food);

    void updateFood(Food food);

    void deleteFood(@Param("id") UUID id);

    Food findFoodById(@Param("id") UUID id);

    List<Food> findAllFoods(@Param("q") String q, @Param("limit") int limit, @Param("offset") int offset);

    long countAllFoods(@Param("q") String q);

    List<String> findKnownCitySlugs();

    List<FoodWithScoreRow> findFoodsByCity(
            @Param("citySlug") String citySlug,
            @Param("minScore") int minScore,
            @Param("limit") int limit,
            @Param("offset") int offset);

    FoodWithScoreRow findFoodDetailByCity(@Param("foodId") UUID foodId, @Param("citySlug") String citySlug);

    void insertCityScore(FoodCityScore score);

    void updateCityScore(FoodCityScore score);

    void deleteCityScore(@Param("id") UUID id);

    FoodCityScore findCityScoreById(@Param("id") UUID id);

    List<FoodCityScore> findCityScoresByFoodId(@Param("foodId") UUID foodId);

    void insertPlaceFood(@Param("placeId") UUID placeId, @Param("foodId") UUID foodId);

    void deletePlaceFood(@Param("placeId") UUID placeId, @Param("foodId") UUID foodId);

    long countLinkedPlaces(@Param("foodId") UUID foodId, @Param("citySlugJson") String citySlugJson);

    List<FoodPlaceRow> findPlacesByFood(
            @Param("foodId") UUID foodId,
            @Param("citySlugJson") String citySlugJson,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("useDistance") boolean useDistance,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countPlacesByFood(
            @Param("foodId") UUID foodId,
            @Param("citySlugJson") String citySlugJson);

    List<FoodPlaceRow> findLinkedPlacesAdmin(
            @Param("foodId") UUID foodId,
            @Param("citySlugJson") String citySlugJson,
            @Param("limit") int limit,
            @Param("offset") int offset);

    List<FoodTagRow> findFoodTagsByPlaceIds(@Param("placeIds") List<UUID> placeIds);

    List<FoodWithScoreRow> findFoodsByPlaceId(
            @Param("placeId") UUID placeId,
            @Param("citySlug") String citySlug);
}
