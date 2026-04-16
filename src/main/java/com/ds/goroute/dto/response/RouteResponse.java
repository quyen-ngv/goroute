package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private Double distanceKm;
    private Integer durationMinutes;
    private String polyline;
    private List<Step> steps;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private String instruction;
        private Double distanceKm;
        private Integer durationMinutes;
        private String travelMode;
    }
}
