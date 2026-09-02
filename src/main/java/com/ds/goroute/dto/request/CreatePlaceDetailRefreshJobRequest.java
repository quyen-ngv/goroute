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
public class CreatePlaceDetailRefreshJobRequest {
    private UUID placeId;

    @Min(1)
    @Max(10000)
    private Integer maxPlaces;

    @Builder.Default
    private Boolean headless = true;

    @Builder.Default
    private Boolean continueOnError = true;

    /** Refresh INACTIVE places too. Off by default: a sweep normally means the live catalogue. */
    @Builder.Default
    private Boolean includeInactive = false;
}
