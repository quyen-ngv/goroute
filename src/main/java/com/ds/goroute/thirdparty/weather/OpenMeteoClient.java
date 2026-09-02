package com.ds.goroute.thirdparty.weather;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

/**
 * Open-Meteo client for current conditions, a short daily outlook and air quality.
 *
 * <p>The free tier needs no API key and no sign-up. {@code weather.open-meteo.model}
 * selects the numerical model: {@code best_match} (default) is Open-Meteo's own
 * seamless blend at the highest resolution available for the point, which is what we
 * want over Vietnam since no Vietnamese regional model is exposed upstream. Pin
 * {@code ecmwf_ifs025} to force a single deterministic model instead.
 */
@Component
@RequiredArgsConstructor
public class OpenMeteoClient {

    private static final String CURRENT_FIELDS = String.join(",",
        "temperature_2m",
        "apparent_temperature",
        "relative_humidity_2m",
        "precipitation",
        "rain",
        "showers",
        "weather_code",
        "cloud_cover",
        "pressure_msl",
        "surface_pressure",
        "wind_speed_10m",
        "wind_direction_10m",
        "wind_gusts_10m",
        "is_day"
    );

    private static final String DAILY_FIELDS = String.join(",",
        "weather_code",
        "temperature_2m_max",
        "temperature_2m_min",
        "apparent_temperature_max",
        "precipitation_sum",
        "precipitation_probability_max",
        "wind_speed_10m_max",
        "wind_gusts_10m_max",
        "uv_index_max",
        "sunrise",
        "sunset"
    );

    private static final String AIR_QUALITY_FIELDS = String.join(",",
        "pm2_5",
        "pm10",
        "carbon_monoxide",
        "nitrogen_dioxide",
        "sulphur_dioxide",
        "ozone",
        "dust",
        "uv_index",
        "us_aqi",
        "european_aqi"
    );

    private final RestClient.Builder restClientBuilder;

    @Value("${weather.open-meteo.base-url:https://api.open-meteo.com}")
    private String baseUrl;

    @Value("${weather.open-meteo.air-quality-base-url:https://air-quality-api.open-meteo.com}")
    private String airQualityBaseUrl;

    @Value("${weather.open-meteo.model:best_match}")
    private String model;

    @Value("${weather.open-meteo.forecast-days:5}")
    private int forecastDays;

    @Value("${weather.open-meteo.connect-timeout-ms:3000}")
    private long connectTimeoutMillis;

    @Value("${weather.open-meteo.read-timeout-ms:5000}")
    private long readTimeoutMillis;

    private RestClient restClient;

    /** Built once so every call reuses the same timeout-bounded factory. */
    @PostConstruct
    void initRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        this.restClient = restClientBuilder.clone()
            .requestFactory(requestFactory)
            .build();
    }

    public OpenMeteoWeatherResponse getCurrentWeather(BigDecimal latitude, BigDecimal longitude) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/v1/forecast")
            .queryParam("latitude", latitude.toPlainString())
            .queryParam("longitude", longitude.toPlainString())
            .queryParam("current", CURRENT_FIELDS)
            .queryParam("daily", DAILY_FIELDS)
            .queryParam("forecast_days", forecastDays)
            .queryParam("timezone", "auto");
        if (model != null && !model.isBlank() && !"best_match".equals(model)) {
            builder.queryParam("models", model);
        }

        URI uri = builder.build().encode().toUri();
        return restClient.get().uri(uri).retrieve().body(OpenMeteoWeatherResponse.class);
    }

    public OpenMeteoAirQualityResponse getAirQuality(BigDecimal latitude, BigDecimal longitude) {
        URI uri = UriComponentsBuilder.fromUriString(airQualityBaseUrl)
            .path("/v1/air-quality")
            .queryParam("latitude", latitude.toPlainString())
            .queryParam("longitude", longitude.toPlainString())
            .queryParam("current", AIR_QUALITY_FIELDS)
            .queryParam("timezone", "auto")
            .build()
            .encode()
            .toUri();

        return restClient.get().uri(uri).retrieve().body(OpenMeteoAirQualityResponse.class);
    }
}
