package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** What one run of the geo backfill changed, step by step, so an operator can read it back. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoBackfillResponse {
    private String datasetVersion;
    private long durationMs;
    /** Step name -> rows affected, in execution order. */
    private Map<String, Long> steps;
    private long placesWithoutWard;
    private long checkinsWithoutWard;
}
