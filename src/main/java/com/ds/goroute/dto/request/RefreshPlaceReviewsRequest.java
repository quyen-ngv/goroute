package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshPlaceReviewsRequest {
    @NotNull(message = "placeId is required")
    private UUID placeId;

    @NotBlank(message = "googlePlaceId is required")
    private String googlePlaceId;

    @NotNull(message = "scrapedAt is required")
    private OffsetDateTime scrapedAt;

    @NotNull(message = "reviews is required")
    @Size(max = 200, message = "At most 200 scraped reviews are accepted")
    @Valid
    private List<ReviewInput> reviews;
}
