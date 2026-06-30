package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodWithScoreRow {
    private UUID id;
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    private String category;
    private String imageUrl;
    private String introductionImages;
    private Integer score;
    private String citySlug;
    private String localDescription;
    private String flavorProfile;
    private String funFact;
}
