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
public class DistanceMatrixResponse {
    private List<String> origins;
    private List<String> destinations;
    private List<List<Element>> rows;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Element {
        private Double distanceKm;
        private Integer durationMinutes;
        private String status;
    }
}
