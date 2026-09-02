package com.ds.goroute.service.impl;

import com.ds.goroute.service.WeatherSnapshotProvider;
import com.ds.goroute.thirdparty.weather.OpenMeteoAirQualityResponse;
import com.ds.goroute.thirdparty.weather.OpenMeteoClient;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherSnapshotProviderImpl implements WeatherSnapshotProvider {

    private final OpenMeteoClient openMeteoClient;

    @Override
    @Cacheable(
        cacheNames = "weatherSnapshot",
        cacheManager = "weatherCacheManager",
        key = "T(com.ds.goroute.utils.GeoGridKey).of(#latitude, #longitude)",
        sync = true
    )
    public WeatherSnapshot getSnapshot(BigDecimal latitude, BigDecimal longitude) {
        OpenMeteoWeatherResponse weather = openMeteoClient.getCurrentWeather(latitude, longitude);
        if (weather == null || weather.getCurrent() == null) {
            throw new RestClientException("Open-Meteo returned an empty forecast response");
        }
        return new WeatherSnapshot(weather, fetchAirQualityQuietly(latitude, longitude));
    }

    /** Air quality is a nice-to-have: losing it must not cost the caller the forecast. */
    private OpenMeteoAirQualityResponse fetchAirQualityQuietly(BigDecimal latitude, BigDecimal longitude) {
        try {
            OpenMeteoAirQualityResponse airQuality = openMeteoClient.getAirQuality(latitude, longitude);
            return airQuality == null || airQuality.getCurrent() == null ? null : airQuality;
        } catch (RestClientException exception) {
            log.warn("Air quality unavailable for {},{}", latitude, longitude, exception);
            return null;
        }
    }
}
