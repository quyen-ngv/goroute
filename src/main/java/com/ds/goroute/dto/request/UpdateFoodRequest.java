package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.FoodCoreIngredientDto;
import com.ds.goroute.dto.response.FoodHowToEatStepDto;
import com.ds.goroute.dto.response.FoodVarietyDto;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFoodRequest {
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    private String category;
    private String imageUrl;
    private List<PlaceImagesDto> introductionImages;
    private String subtitleVi;
    private String subtitleEn;
    private String subtitleJa;
    private String subtitleKo;
    private String heroTaglineVi;
    private String heroTaglineEn;
    private String heroTaglineJa;
    private String heroTaglineKo;
    private String themeColor;
    private List<String> tags;
    private java.math.BigDecimal priceMin;
    private java.math.BigDecimal priceMax;
    private String priceCurrency;
    private List<FoodCoreIngredientDto> coreIngredients;
    private List<FoodVarietyDto> varieties;
    private String historyOrigin;
    private String ingredientsPreparation;
    private String funFact;
    private String howToEatDescription;
    private List<FoodHowToEatStepDto> howToEatSteps;
}
