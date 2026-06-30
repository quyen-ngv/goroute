package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingContributionReview {
    private UUID id;
    private UUID contributionId;
    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;
    private String text;
    private String photos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
