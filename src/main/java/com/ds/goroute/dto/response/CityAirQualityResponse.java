package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Current air quality at a location; null when the air-quality upstream is unavailable. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityAirQualityResponse {
    private String observedAt;
    private Double pm25;
    private Double pm10;
    private Double ozone;
    private Double nitrogenDioxide;
    private Double sulphurDioxide;
    private Double carbonMonoxide;
    private Double dust;
    private Double uvIndex;
    private Integer usAqi;
    private Integer europeanAqi;
    /** US AQI band, English. */
    private String category;
    /** US AQI band, Vietnamese. */
    private String categoryVi;
    /** Stable token for FE colouring: good | moderate | sensitive | unhealthy | very_unhealthy | hazardous. */
    private String categoryCode;
    private String advice;
    private String adviceVi;
}
