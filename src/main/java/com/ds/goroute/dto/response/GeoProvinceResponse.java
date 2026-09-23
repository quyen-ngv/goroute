package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One of the 34 provinces, with the bounding box the app uses to decide which ward layers to load. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoProvinceResponse {
    private String code;
    private String name;
    private String nameEn;
    private String fullName;
    private String region;
    /** A point inside the province (not the centroid, which can fall in the sea). */
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal areaKm2;
    private Integer wardCount;
    private Double minLatitude;
    private Double minLongitude;
    private Double maxLatitude;
    private Double maxLongitude;
    /** False until the boundary dataset has been loaded for this province. */
    private Boolean hasBoundary;
}
