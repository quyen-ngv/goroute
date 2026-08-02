package com.ds.goroute.thirdparty.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

@Component
@RequiredArgsConstructor
public class OpenMeteoClient {

    private static final String CURRENT_FIELDS = String.join(",",
        "temperature_2m",
        "apparent_temperature",
        "relative_humidity_2m",
        "precipitation",
        "rain",
        "weather_code",
        "cloud_cover",
        "wind_speed_10m",
        "wind_direction_10m",
        "is_day"
    );

    private final RestClient.Builder restClientBuilder;

    @Value("${weather.open-meteo.base-url:https://api.open-meteo.com}")
    private String baseUrl;

    public OpenMeteoWeatherResponse getCurrentWeather(BigDecimal latitude, BigDecimal longitude) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/v1/forecast")
            .queryParam("latitude", latitude.toPlainString())
            .queryParam("longitude", longitude.toPlainString())
            .queryParam("current", CURRENT_FIELDS)
            .queryParam("timezone", "auto")
            .build()
            .encode()
            .toUri();

        return restClientBuilder.build()
            .get()
            .uri(uri)
            .retrieve()
            .body(OpenMeteoWeatherResponse.class);
    }
}
