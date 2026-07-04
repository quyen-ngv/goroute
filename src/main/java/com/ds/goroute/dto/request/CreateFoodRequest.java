package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.FoodCoreIngredientDto;
import com.ds.goroute.dto.response.FoodHowToEatStepDto;
import com.ds.goroute.dto.response.FoodVarietyDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateFoodRequest {
    @NotBlank
    private String nameVi;
    @NotBlank
    private String nameEn;
    private String nameJa;
    private String nameKo;
    @NotBlank
    private String description;
    @NotBlank
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
    private Long priceMin;
    private Long priceMax;
    private String priceCurrency;
    private List<FoodCoreIngredientDto> coreIngredients;
    private List<FoodVarietyDto> varieties;
    private String historyOrigin;
    private String ingredientsPreparation;
    private String funFact;
    private String howToEatDescription;
    private List<FoodHowToEatStepDto> howToEatSteps;
}
