package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.PlaceImagesDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateFoodCityScoreRequest {
    private String citySlug;
    @Min(0)
    @Max(100)
    private Integer score;
    private String localDescription;
    private String imageUrl;
    private List<PlaceImagesDto> introductionImages;
    private Map<String, Object> flavorProfile;
    private String funFact;
}
