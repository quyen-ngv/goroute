package com.ds.goroute.thirdparty.google.places;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Body of {@code POST /v1/places:searchText}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GooglePlacesTextSearchRequest {
    private String textQuery;
    private Integer pageSize;
    private LocationBias locationBias;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationBias {
        private Circle circle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Circle {
        private Center center;
        /** Metres, 0 to 50,000 for a bias. */
        private Double radius;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Center {
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}
