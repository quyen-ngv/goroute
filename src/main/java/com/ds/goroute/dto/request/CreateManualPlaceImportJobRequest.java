package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateManualPlaceImportJobRequest {
    @NotNull
    private UUID activityId;

    @NotBlank
    @Pattern(regexp = "https?://.+", message = "googleMapsUrl must be an HTTP(S) URL")
    private String googleMapsUrl;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer maxReviews = 5;
}
