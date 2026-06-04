package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FoodSummaryResponse {
    private UUID id;
    private String name;
    private String description;
    private Integer score;
    private String scoreLabelKey;
    private String imageUrl;
    private String category;
    private String citySlug;
}
