package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code route_leg_cache}: a road leg between two points for one profile. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteLegCacheEntry {
    private String legKey;
    private String provider;
    private String profile;
    private Double fromLat;
    private Double fromLng;
    private Double toLat;
    private Double toLng;
    private Integer distanceM;
    private Integer durationS;
    /** Encoded polyline, precision 6. */
    private String polyline;
}
