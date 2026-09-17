package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of one auto-map run. A dry run reports exactly the same numbers as a real run
 * because it executes the same statements and rolls the transaction back, rather than
 * estimating with a parallel set of COUNT queries that could drift from the real ones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAreaAutoMapResponse {

    private boolean dryRun;
    private List<TargetResult> targets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetResult {
        private String target;
        private String label;
        /** Rows resolved from their own coordinates, or inherited from a Place. */
        private int byCoordinates;
        /** Rows resolved from the area name appearing in their address text. */
        private int byName;
        /** Rows that still have no area after this run. */
        private long stillUnmapped;
    }
}
