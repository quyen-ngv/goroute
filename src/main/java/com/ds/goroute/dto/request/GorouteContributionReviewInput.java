package com.ds.goroute.dto.request;

import jakarta.validation.constraints.*;
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
public class GorouteContributionReviewInput {

    @NotNull
    private UUID contributionId;

    @NotNull
    private UUID userId;

    @NotNull
    @Min(1) @Max(5)
    private Integer overallRating;

    @Min(1) @Max(5)
    private Integer foodRating;

    @Min(1) @Max(5)
    private Integer priceRating;

    @Min(1) @Max(5)
    private Integer ambianceRating;

    @Min(1) @Max(5)
    private Integer serviceRating;

    @Size(max = 2000)
    private String text;

    private List<String> photos;
}
