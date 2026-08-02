package com.ds.goroute.thirdparty.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenMeteoWeatherResponse {
    private String timezone;
    private Current current;

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
        private Integer weatherCode;
        private Integer cloudCover;
        @JsonProperty("wind_speed_10m")
        private Double windSpeed10m;
        @JsonProperty("wind_direction_10m")
        private Integer windDirection10m;
        private Integer isDay;
    }
}
