package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFoodDetailResponse {
    private UUID id;
    private Map<String, String> names;
    private String description;
    private String category;
    private String imageUrl;
    private List<FoodCityScoreResponse> cityScores;
    private long linkedPlacesCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
