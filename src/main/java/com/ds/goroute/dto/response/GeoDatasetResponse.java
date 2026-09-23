package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Which boundary release is loaded and whether the code-level backfill has run against it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoDatasetResponse {
    private String version;
    private String decree;
    private LocalDateTime generatedAt;
    private LocalDateTime importedAt;
    private LocalDateTime backfilledAt;
    private Integer provinceCount;
    private Integer wardCount;
    /** Live counts, so a half-loaded dataset is visible as such. */
    private Long provincesActive;
    private Long wardsActive;
    private Long placesWithoutWard;
    private Long checkinsWithoutWard;
}
