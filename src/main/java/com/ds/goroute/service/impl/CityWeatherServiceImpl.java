package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.thirdparty.weather.OpenMeteoClient;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CityWeatherServiceImpl implements CityWeatherService {

    private static final String PROVIDER = "Open-Meteo";
    private static final String ATTRIBUTION_URL = "https://open-meteo.com/";

    private final LocationImageRepository locationImageRepository;
    private final OpenMeteoClient openMeteoClient;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "cityWeather", key = "#locationId",
        cacheManager = "weatherCacheManager", sync = true)
    public CityWeatherResponse getCurrentWeather(UUID locationId) {
        LocationImage location = locationImageRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));

        if (location.getLatitude() == null || location.getLongitude() == null) {
            throw new BusinessException(
                ErrorConstant.BAD_REQUEST,
                "Latitude and longitude are required to retrieve city weather",
                HttpStatus.BAD_REQUEST
            );
        }

        try {
            OpenMeteoWeatherResponse response = openMeteoClient.getCurrentWeather(
                location.getLatitude(), location.getLongitude());
            if (response == null || response.getCurrent() == null) {
                throw new RestClientException("Open-Meteo returned an empty response");
            }
            return mapResponse(location, response);
        } catch (RestClientException exception) {
            log.error("Failed to retrieve weather for location {}", locationId, exception);
            throw new BusinessException(
                ErrorConstant.INTERNAL_SERVER_ERROR,
                "Weather provider is temporarily unavailable",
                HttpStatus.BAD_GATEWAY
            );
        }
    }

    private CityWeatherResponse mapResponse(
        LocationImage location,
        OpenMeteoWeatherResponse response
    ) {
        OpenMeteoWeatherResponse.Current current = response.getCurrent();
        return CityWeatherResponse.builder()
            .locationId(location.getId())
            .city(location.getFullAddress())
            .citySlug(location.getCitySlug())
            .latitude(location.getLatitude())
            .longitude(location.getLongitude())
            .timezone(response.getTimezone())
            .observedAt(current.getTime())
            .temperatureC(current.getTemperature2m())
            .apparentTemperatureC(current.getApparentTemperature())
            .relativeHumidityPercent(current.getRelativeHumidity2m())
            .precipitationMm(current.getPrecipitation())
            .rainMm(current.getRain())
            .cloudCoverPercent(current.getCloudCover())
            .windSpeedKmh(current.getWindSpeed10m())
            .windDirectionDegrees(current.getWindDirection10m())
            .weatherCode(current.getWeatherCode())
            .condition(conditionFor(current.getWeatherCode()))
            .day(current.getIsDay() == null ? null : current.getIsDay() == 1)
            .provider(PROVIDER)
            .attributionUrl(ATTRIBUTION_URL)
            .build();
    }

    private String conditionFor(Integer weatherCode) {
        if (weatherCode == null) {
            return "Unknown";
        }
        return switch (weatherCode) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }
}
