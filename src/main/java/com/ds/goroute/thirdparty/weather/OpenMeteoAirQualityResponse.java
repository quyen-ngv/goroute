package com.ds.goroute.thirdparty.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response of the Open-Meteo air-quality endpoint (CAMS global/European model). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenMeteoAirQualityResponse {
    private String timezone;
    private Current current;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Current {
        private String time;
        @JsonProperty("pm2_5")
        private Double pm25;
        @JsonProperty("pm10")
        private Double pm10;
        private Double carbonMonoxide;
        private Double nitrogenDioxide;
        private Double sulphurDioxide;
        private Double ozone;
        private Double dust;
        private Double uvIndex;
        private Integer usAqi;
        private Integer europeanAqi;
    }
}
