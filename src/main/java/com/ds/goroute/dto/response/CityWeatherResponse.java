package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityWeatherResponse {
    private UUID locationId;
    private String city;
    private String citySlug;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
    private String observedAt;
    private Double temperatureC;
    private Double apparentTemperatureC;
    private Integer relativeHumidityPercent;
    private Double precipitationMm;
    private Double rainMm;
    private Integer cloudCoverPercent;
    private Double windSpeedKmh;
    private Integer windDirectionDegrees;
    private Integer weatherCode;
    private String condition;
    private Boolean day;
    private String provider;
    private String attributionUrl;
}
