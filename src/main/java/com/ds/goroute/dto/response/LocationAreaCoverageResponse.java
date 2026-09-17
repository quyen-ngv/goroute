package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** How much of each table currently resolves to a tourist area. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAreaCoverageResponse {

    private List<TargetCoverage> targets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetCoverage {
        private String target;
        private String label;
        private long total;
        private long mapped;
        private long unmapped;
    }
}
