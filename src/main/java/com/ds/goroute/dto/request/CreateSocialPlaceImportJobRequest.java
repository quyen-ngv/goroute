package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateSocialPlaceImportJobRequest {
    private UUID userId;

    /** Explicit opt-in for the admin batch runner. */
    @Builder.Default
    private Boolean allUsers = false;
    private List<UUID> socialJobIds;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer maxReviews = 5;

    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer limit = 50;
}
