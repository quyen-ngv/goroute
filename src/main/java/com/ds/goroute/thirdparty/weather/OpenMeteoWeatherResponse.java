package com.ds.goroute.thirdparty.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenMeteoWeatherResponse {
    private String timezone;
    private Current current;
    private Daily daily;

    public OpenMeteoWeatherResponse(String timezone, Current current) {
        this(timezone, current, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Current {
        private String time;
        @JsonProperty("temperature_2m")
        private Double temperature2m;
        private Double apparentTemperature;
        @JsonProperty("relative_humidity_2m")
        private Integer relativeHumidity2m;
        private Double precipitation;
        private Double rain;
        private Double showers;
        private Integer weatherCode;
        private Integer cloudCover;
        private Double pressureMsl;
        private Double surfacePressure;
        @JsonProperty("wind_speed_10m")
        private Double windSpeed10m;
        @JsonProperty("wind_direction_10m")
        private Integer windDirection10m;
        @JsonProperty("wind_gusts_10m")
        private Double windGusts10m;
        private Integer isDay;
    }

    /** Multi-day outlook; every list is parallel to {@link #time} and may be shorter on partial upstream data. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Daily {
        private List<String> time;
        private List<Integer> weatherCode;
        @JsonProperty("temperature_2m_max")
        private List<Double> temperature2mMax;
        @JsonProperty("temperature_2m_min")
        private List<Double> temperature2mMin;
        @JsonProperty("apparent_temperature_max")
        private List<Double> apparentTemperatureMax;
        private List<Double> precipitationSum;
        private List<Integer> precipitationProbabilityMax;
        @JsonProperty("wind_speed_10m_max")
        private List<Double> windSpeed10mMax;
        @JsonProperty("wind_gusts_10m_max")
        private List<Double> windGusts10mMax;
        private List<Double> uvIndexMax;
        private List<String> sunrise;
        private List<String> sunset;
    }
}
