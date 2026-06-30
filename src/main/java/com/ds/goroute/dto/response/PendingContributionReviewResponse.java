package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingContributionReviewResponse {
    private UUID id;
    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;
    private String text;
    private List<String> photos;
}
