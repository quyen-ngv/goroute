package com.ds.goroute.service;

import com.ds.goroute.thirdparty.weather.OpenMeteoAirQualityResponse;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;

import java.math.BigDecimal;

/**
 * Caching gateway in front of Open-Meteo.
 *
 * <p>Sits between {@link CityWeatherService} and the HTTP client so the cache is keyed by
 * grid cell rather than by location id: several curated location images in the same city
 * then cost a single upstream call.
 */
public interface WeatherSnapshotProvider {

    /**
     * Fetch current conditions, the daily outlook and air quality for a point.
     *
     * <p>Air quality is best-effort: if only that upstream fails, the snapshot still
     * carries the weather and {@link WeatherSnapshot#airQuality()} is {@code null}.
     *
     * @throws org.springframework.web.client.RestClientException when the forecast upstream fails
     */
    WeatherSnapshot getSnapshot(BigDecimal latitude, BigDecimal longitude);

    /**
     * Fetch a fresh snapshot and replace the cached value for its grid cell.
     *
     * <p>Used by the scheduled cache warmer. Unlike {@link #getSnapshot(BigDecimal, BigDecimal)},
     * this operation always calls Open-Meteo even when the cell is already cached.
     */
    WeatherSnapshot refreshSnapshot(BigDecimal latitude, BigDecimal longitude);

    record WeatherSnapshot(
        OpenMeteoWeatherResponse weather,
        OpenMeteoAirQualityResponse airQuality
    ) {
    }
}
