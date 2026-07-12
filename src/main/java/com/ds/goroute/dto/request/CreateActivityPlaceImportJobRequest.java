package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityPlaceImportJobRequest {
    private UUID userId;

    /** Explicit opt-in for the admin batch runner. */
    @Builder.Default
    private Boolean allUsers = false;
    private UUID tripId;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer maxReviews = 5;

    @Min(1)
    @Max(200)
    @Builder.Default
    private Integer limit = 100;
}
