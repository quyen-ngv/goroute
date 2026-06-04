package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodCityScoreResponse {
    private UUID id;
    private String citySlug;
    private Integer score;
    private String localDescription;
    private String imageUrl;
    private Map<String, Object> flavorProfile;
    private String funFact;
}
