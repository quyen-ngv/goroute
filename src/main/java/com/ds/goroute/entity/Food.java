package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Food {
    private UUID id;
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    private String category;
    private String imageUrl;
    private String introductionImages;
    private String subtitleVi;
    private String subtitleEn;
    private String subtitleJa;
    private String subtitleKo;
    private String heroTaglineVi;
    private String heroTaglineEn;
    private String heroTaglineJa;
    private String heroTaglineKo;
    private Long priceMin;
    private Long priceMax;
    private String priceCurrency;
    private String coreIngredients;
    private String varieties;
    private String historyOrigin;
    private String ingredientsPreparation;
    private String funFact;
    private String howToEatDescription;
    private String howToEatSteps;
    private String themeColor;
    private String tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
