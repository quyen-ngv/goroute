package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.CityAirQualityResponse;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.WeatherSnapshotProvider;
import com.ds.goroute.service.WeatherSnapshotProvider.WeatherSnapshot;
import com.ds.goroute.thirdparty.weather.OpenMeteoAirQualityResponse;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;
import com.ds.goroute.utils.WeatherAdvisoryEvaluator;
import com.ds.goroute.utils.WeatherCodeCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CityWeatherServiceImpl implements CityWeatherService {

    private static final String PROVIDER = "Open-Meteo";
    private static final String ATTRIBUTION_URL = "https://open-meteo.com/";
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final LocationImageRepository locationImageRepository;
    private final WeatherSnapshotProvider weatherSnapshotProvider;

    @Override
    @Transactional(readOnly = true)
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
            WeatherSnapshot snapshot = weatherSnapshotProvider.getSnapshot(
                location.getLatitude(), location.getLongitude());
            return mapResponse(location, snapshot);
        } catch (RestClientException exception) {
            log.error("Failed to retrieve weather for location {}", locationId, exception);
            throw new BusinessException(
                ErrorConstant.INTERNAL_SERVER_ERROR,
                "Weather provider is temporarily unavailable",
                HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public CityWeatherResponse getCurrentWeather(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
            || latitude.abs().compareTo(MAX_LATITUDE) > 0
            || longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
            throw new BusinessException(
                ErrorConstant.BAD_REQUEST,
                "A latitude in [-90, 90] and a longitude in [-180, 180] are required to retrieve weather",
                HttpStatus.BAD_REQUEST
            );
        }

        try {
            WeatherSnapshot snapshot = weatherSnapshotProvider.getSnapshot(latitude, longitude);
            return mapResponse(null, null, null, latitude, longitude, snapshot);
        } catch (RestClientException exception) {
            log.error("Failed to retrieve weather for {},{}", latitude, longitude, exception);
            throw new BusinessException(
                ErrorConstant.INTERNAL_SERVER_ERROR,
                "Weather provider is temporarily unavailable",
                HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public CityWeatherResponse findWeatherQuietly(LocationImage location) {
        if (location == null || location.getLatitude() == null || location.getLongitude() == null) {
            return null;
        }
        try {
            WeatherSnapshot snapshot = weatherSnapshotProvider.getSnapshot(
                location.getLatitude(), location.getLongitude());
            return mapResponse(location, snapshot);
        } catch (RestClientException exception) {
            log.warn("Weather unavailable for location {}; returning the image without it",
                location.getId(), exception);
            return null;
        }
    }

    private CityWeatherResponse mapResponse(LocationImage location, WeatherSnapshot snapshot) {
        return mapResponse(
            location.getId(),
            location.getFullAddress(),
            location.getCitySlug(),
            location.getLatitude(),
            location.getLongitude(),
            snapshot);
    }

    private CityWeatherResponse mapResponse(
        UUID locationId,
        String city,
        String citySlug,
        BigDecimal latitude,
        BigDecimal longitude,
        WeatherSnapshot snapshot
    ) {
        OpenMeteoWeatherResponse weather = snapshot.weather();
        OpenMeteoWeatherResponse.Current current = weather.getCurrent();
        Boolean isDay = current.getIsDay() == null ? null : current.getIsDay() == 1;

        List<DailyForecastResponse> daily = mapDaily(weather.getDaily());
        CityAirQualityResponse airQuality = mapAirQuality(snapshot.airQuality());

        return CityWeatherResponse.builder()
            .locationId(locationId)
            .city(city)
            .citySlug(citySlug)
            .latitude(latitude)
            .longitude(longitude)
            .timezone(weather.getTimezone())
            .observedAt(current.getTime())
            .temperatureC(current.getTemperature2m())
            .apparentTemperatureC(current.getApparentTemperature())
            .relativeHumidityPercent(current.getRelativeHumidity2m())
            .precipitationMm(current.getPrecipitation())
            .rainMm(current.getRain())
            .showersMm(current.getShowers())
            .cloudCoverPercent(current.getCloudCover())
            .pressureMslHpa(current.getPressureMsl())
            .surfacePressureHpa(current.getSurfacePressure())
            .windSpeedKmh(current.getWindSpeed10m())
            .windGustsKmh(current.getWindGusts10m())
            .windDirectionDegrees(current.getWindDirection10m())
            .windDirectionLabel(WeatherAdvisoryEvaluator.windDirectionLabel(current.getWindDirection10m()))
            .windForceBeaufort(WeatherAdvisoryEvaluator.beaufortForce(current.getWindSpeed10m()))
            .weatherCode(current.getWeatherCode())
            .condition(WeatherCodeCatalog.description(current.getWeatherCode()))
            .conditionVi(WeatherCodeCatalog.descriptionVi(current.getWeatherCode()))
            .icon(WeatherCodeCatalog.icon(current.getWeatherCode(), isDay))
            .day(isDay)
            .airQuality(airQuality)
            .daily(daily)
            .alerts(WeatherAdvisoryEvaluator.evaluate(
                current.getWindGusts10m(),
                current.getWeatherCode(),
                daily.isEmpty() ? null : daily.get(0),
                airQuality))
            .provider(PROVIDER)
            .attributionUrl(ATTRIBUTION_URL)
            .build();
    }

    /** Open-Meteo returns parallel arrays; a short or absent array simply drops that field. */
    private List<DailyForecastResponse> mapDaily(OpenMeteoWeatherResponse.Daily daily) {
        if (daily == null || daily.getTime() == null || daily.getTime().isEmpty()) {
            return List.of();
        }

        List<DailyForecastResponse> days = new ArrayList<>(daily.getTime().size());
        for (int index = 0; index < daily.getTime().size(); index++) {
            Integer weatherCode = valueAt(daily.getWeatherCode(), index);
            days.add(DailyForecastResponse.builder()
                .date(daily.getTime().get(index))
                .weatherCode(weatherCode)
                .condition(WeatherCodeCatalog.description(weatherCode))
                .conditionVi(WeatherCodeCatalog.descriptionVi(weatherCode))
                .icon(WeatherCodeCatalog.icon(weatherCode, Boolean.TRUE))
                .temperatureMaxC(valueAt(daily.getTemperature2mMax(), index))
                .temperatureMinC(valueAt(daily.getTemperature2mMin(), index))
                .apparentTemperatureMaxC(valueAt(daily.getApparentTemperatureMax(), index))
                .precipitationSumMm(valueAt(daily.getPrecipitationSum(), index))
                .precipitationProbabilityPercent(valueAt(daily.getPrecipitationProbabilityMax(), index))
                .windSpeedMaxKmh(valueAt(daily.getWindSpeed10mMax(), index))
                .windGustsMaxKmh(valueAt(daily.getWindGusts10mMax(), index))
                .uvIndexMax(valueAt(daily.getUvIndexMax(), index))
                .sunrise(valueAt(daily.getSunrise(), index))
                .sunset(valueAt(daily.getSunset(), index))
                .build());
        }
        return days;
    }

    private CityAirQualityResponse mapAirQuality(OpenMeteoAirQualityResponse airQuality) {
        if (airQuality == null || airQuality.getCurrent() == null) {
            return null;
        }
        OpenMeteoAirQualityResponse.Current current = airQuality.getCurrent();
        String categoryCode = WeatherAdvisoryEvaluator.aqiCategoryCode(current.getUsAqi());

        return CityAirQualityResponse.builder()
            .observedAt(current.getTime())
            .pm25(current.getPm25())
            .pm10(current.getPm10())
            .ozone(current.getOzone())
            .nitrogenDioxide(current.getNitrogenDioxide())
            .sulphurDioxide(current.getSulphurDioxide())
            .carbonMonoxide(current.getCarbonMonoxide())
            .dust(current.getDust())
            .uvIndex(current.getUvIndex())
            .usAqi(current.getUsAqi())
            .europeanAqi(current.getEuropeanAqi())
            .categoryCode(categoryCode)
            .category(WeatherAdvisoryEvaluator.aqiCategory(categoryCode))
            .categoryVi(WeatherAdvisoryEvaluator.aqiCategoryVi(categoryCode))
            .advice(WeatherAdvisoryEvaluator.aqiAdvice(categoryCode))
            .adviceVi(WeatherAdvisoryEvaluator.aqiAdviceVi(categoryCode))
            .build();
    }

    private static <T> T valueAt(List<T> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }
}
