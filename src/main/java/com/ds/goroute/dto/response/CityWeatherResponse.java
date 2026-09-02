package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
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
    private Double showersMm;
    private Integer cloudCoverPercent;
    private Double pressureMslHpa;
    private Double surfacePressureHpa;
    private Double windSpeedKmh;
    private Double windGustsKmh;
    private Integer windDirectionDegrees;
    /** 16-point compass label for the wind direction, e.g. {@code NE}. */
    private String windDirectionLabel;
    /** Beaufort force of the sustained wind, 0-17. */
    private Integer windForceBeaufort;

    private Integer weatherCode;
    private String condition;
    private String conditionVi;
    /** Stable icon token for the FE, e.g. {@code rain-day}, {@code thunderstorm}. */
    private String icon;
    private Boolean day;

    private CityAirQualityResponse airQuality;
    private List<DailyForecastResponse> daily;
    /** Empty when nothing noteworthy; never null. */
    private List<WeatherAlertResponse> alerts;

    private String provider;
    private String attributionUrl;
}
