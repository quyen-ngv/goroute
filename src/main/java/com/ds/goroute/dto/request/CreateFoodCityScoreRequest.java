package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateFoodCityScoreRequest {
    @NotBlank
    private String citySlug;
    @NotNull
    @Min(0)
    @Max(100)
    private Integer score;
    private String localDescription;
    private String imageUrl;
    private Map<String, Object> flavorProfile;
    private String funFact;
}
