package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateFoodCityScoreRequest {
    private String citySlug;
    @Min(0)
    @Max(100)
    private Integer score;
    private String localDescription;
    private String imageUrl;
    private Map<String, Object> flavorProfile;
    private String funFact;
}
