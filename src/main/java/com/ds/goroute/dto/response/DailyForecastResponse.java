package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One day of the short outlook, in the location's own timezone. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyForecastResponse {
    private String date;
    private Integer weatherCode;
    private String condition;
    private String conditionVi;
    private String icon;
    private Double temperatureMaxC;
    private Double temperatureMinC;
    private Double apparentTemperatureMaxC;
    private Double precipitationSumMm;
    private Integer precipitationProbabilityPercent;
    private Double windSpeedMaxKmh;
    private Double windGustsMaxKmh;
    private Double uvIndexMax;
    private String sunrise;
    private String sunset;
}
