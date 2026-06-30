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
public class FoodCityScore {
    private UUID id;
    private UUID foodId;
    private String citySlug;
    private Integer score;
    private String localDescription;
    private String imageUrl;
    private String introductionImages;
    private String flavorProfile;
    private String funFact;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
