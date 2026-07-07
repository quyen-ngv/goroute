package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private String generalDescription;
    private String localDescription;
    private Integer score;
    private String scoreLabelKey;
    private String category;
    private String imageUrl;
    private List<PlaceImagesDto> introductionImages;
    private String subtitle;
    private String heroTagline;
    private String themeColor;
    private List<String> tags;
    private java.math.BigDecimal priceMin;
    private java.math.BigDecimal priceMax;
    private String priceCurrency;
    private List<FoodCoreIngredientDto> coreIngredients;
    private List<FoodVarietyDto> varieties;
    private String historyOrigin;
    private String ingredientsPreparation;
    private String howToEatDescription;
    private List<FoodHowToEatStepDto> howToEatSteps;
    private List<CitySlugOptionResponse> cities;
    private String citySlug;
    private String cityDisplayName;
    private Map<String, Object> flavorProfile;
    private String funFact;
}
