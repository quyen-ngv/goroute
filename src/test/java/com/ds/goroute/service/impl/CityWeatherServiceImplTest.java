package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.WeatherSnapshotProvider;
import com.ds.goroute.service.WeatherSnapshotProvider.WeatherSnapshot;
import com.ds.goroute.thirdparty.weather.OpenMeteoAirQualityResponse;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityWeatherServiceImplTest {

    @Mock
    private LocationImageRepository locationImageRepository;

    @Mock
    private WeatherSnapshotProvider weatherSnapshotProvider;

    @InjectMocks
    private CityWeatherServiceImpl service;

    private static LocationImage daNang(UUID locationId) {
        return LocationImage.builder()
            .id(locationId)
            .fullAddress("Đà Nẵng")
            .citySlug("danang")
            .latitude(new BigDecimal("16.05440000"))
            .longitude(new BigDecimal("108.20220000"))
            .build();
    }

    private static OpenMeteoWeatherResponse.Current rainyAfternoon() {
        OpenMeteoWeatherResponse.Current current = new OpenMeteoWeatherResponse.Current();
        current.setTime("2026-07-31T14:15");
        current.setTemperature2m(31.4);
        current.setApparentTemperature(36.2);
        current.setRelativeHumidity2m(72);
        current.setPrecipitation(0.2);
        current.setRain(0.2);
        current.setWeatherCode(61);
        current.setCloudCover(80);
        current.setWindSpeed10m(12.5);
        current.setWindDirection10m(135);
        current.setWindGusts10m(24.0);
        current.setIsDay(1);
        return current;
    }

    @Test
    void returnsCurrentWeatherForLocationCoordinates() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = daNang(locationId);
        OpenMeteoWeatherResponse upstream =
            new OpenMeteoWeatherResponse("Asia/Bangkok", rainyAfternoon());

        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(weatherSnapshotProvider.getSnapshot(location.getLatitude(), location.getLongitude()))
            .thenReturn(new WeatherSnapshot(upstream, null));

        CityWeatherResponse result = service.getCurrentWeather(locationId);

        assertThat(result.getLocationId()).isEqualTo(locationId);
        assertThat(result.getTemperatureC()).isEqualTo(31.4);
        assertThat(result.getCondition()).isEqualTo("Rain");
        assertThat(result.getConditionVi()).isEqualTo("Mưa nhỏ");
        assertThat(result.getIcon()).isEqualTo("rain");
        assertThat(result.getWindDirectionLabel()).isEqualTo("SE");
        assertThat(result.getWindForceBeaufort()).isEqualTo(3);
        assertThat(result.getDay()).isTrue();
        assertThat(result.getAirQuality()).isNull();
        assertThat(result.getDaily()).isEmpty();
        assertThat(result.getAlerts()).isEmpty();
        assertThat(result.getProvider()).isEqualTo("Open-Meteo");
    }

    @Test
    void mapsDailyOutlookAndRaisesVietnameseRainAndHeatAdvisories() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = daNang(locationId);

        OpenMeteoWeatherResponse.Daily daily = new OpenMeteoWeatherResponse.Daily();
        daily.setTime(List.of("2026-07-31", "2026-08-01"));
        daily.setWeatherCode(List.of(95, 3));
        daily.setTemperature2mMax(List.of(34.0, 33.0));
        daily.setTemperature2mMin(List.of(26.0, 26.5));
        daily.setApparentTemperatureMax(List.of(40.1, 36.0));
        daily.setPrecipitationSum(List.of(120.0, 4.0));
        daily.setPrecipitationProbabilityMax(List.of(100, 30));
        daily.setWindGusts10mMax(List.of(95.0, 20.0));
        daily.setUvIndexMax(List.of(9.2, 8.4));
        daily.setSunrise(List.of("2026-07-31T05:30", "2026-08-01T05:31"));
        daily.setSunset(List.of("2026-07-31T18:10", "2026-08-01T18:09"));

        OpenMeteoWeatherResponse.Current current = rainyAfternoon();
        current.setWeatherCode(95);
        OpenMeteoWeatherResponse upstream =
            new OpenMeteoWeatherResponse("Asia/Ho_Chi_Minh", current, daily);

        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(weatherSnapshotProvider.getSnapshot(location.getLatitude(), location.getLongitude()))
            .thenReturn(new WeatherSnapshot(upstream, null));

        CityWeatherResponse result = service.getCurrentWeather(locationId);

        assertThat(result.getDaily()).hasSize(2);
        assertThat(result.getDaily().get(0).getPrecipitationSumMm()).isEqualTo(120.0);
        assertThat(result.getDaily().get(1).getWindGustsMaxKmh()).isEqualTo(20.0);

        assertThat(result.getAlerts()).extracting("code")
            .containsExactlyInAnyOrder(
                "STORM_FORCE_WIND", "VERY_HEAVY_RAIN", "EXTREME_HEAT", "THUNDERSTORM", "HIGH_UV");
        assertThat(result.getAlerts().get(0).getLevel()).isEqualTo("DANGER");
    }

    @Test
    void mapsAirQualityWithVietnameseBandAndAdvisory() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = daNang(locationId);

        OpenMeteoAirQualityResponse.Current airQualityCurrent = new OpenMeteoAirQualityResponse.Current();
        airQualityCurrent.setTime("2026-07-31T14:00");
        airQualityCurrent.setPm25(157.9);
        airQualityCurrent.setPm10(167.2);
        airQualityCurrent.setUsAqi(149);
        airQualityCurrent.setEuropeanAqi(88);
        OpenMeteoAirQualityResponse airQuality =
            new OpenMeteoAirQualityResponse("Asia/Ho_Chi_Minh", airQualityCurrent);

        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(weatherSnapshotProvider.getSnapshot(location.getLatitude(), location.getLongitude()))
            .thenReturn(new WeatherSnapshot(
                new OpenMeteoWeatherResponse("Asia/Ho_Chi_Minh", rainyAfternoon()), airQuality));

        CityWeatherResponse result = service.getCurrentWeather(locationId);

        assertThat(result.getAirQuality().getPm25()).isEqualTo(157.9);
        assertThat(result.getAirQuality().getUsAqi()).isEqualTo(149);
        assertThat(result.getAirQuality().getCategoryCode()).isEqualTo("sensitive");
        assertThat(result.getAirQuality().getCategoryVi()).startsWith("Kém");
        assertThat(result.getAlerts()).extracting("code").containsExactly("POOR_AIR_QUALITY");
    }

    @Test
    void rejectsLocationWithoutCoordinates() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = LocationImage.builder().id(locationId).fullAddress("Unknown").build();
        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> service.getCurrentWeather(locationId))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Latitude and longitude are required to retrieve city weather");
        verifyNoInteractions(weatherSnapshotProvider);
    }

    @Test
    void embeddedWeatherIsNullWhenProviderFails() {
        LocationImage location = daNang(UUID.randomUUID());
        when(weatherSnapshotProvider.getSnapshot(any(), any()))
            .thenThrow(new RestClientException("upstream down"));

        assertThat(service.findWeatherQuietly(location)).isNull();
    }

    @Test
    void embeddedWeatherIsNullWhenLocationHasNoCoordinates() {
        LocationImage location = LocationImage.builder().id(UUID.randomUUID()).build();

        assertThat(service.findWeatherQuietly(location)).isNull();
        verifyNoInteractions(weatherSnapshotProvider);
    }

    @Test
    void deserializesOpenMeteoCoordinateSuffixedFields() throws Exception {
        String json = """
            {
              "timezone": "Asia/Bangkok",
              "current": {
                "time": "2026-07-31T14:15",
                "temperature_2m": 31.4,
                "relative_humidity_2m": 72,
                "wind_speed_10m": 12.5,
                "wind_direction_10m": 135,
                "wind_gusts_10m": 24.0
              },
              "daily": {
                "time": ["2026-07-31"],
                "temperature_2m_max": [34.0],
                "precipitation_sum": [12.5],
                "wind_gusts_10m_max": [40.0],
                "uv_index_max": [9.2]
              }
            }
            """;

        OpenMeteoWeatherResponse response = new ObjectMapper()
            .readValue(json, OpenMeteoWeatherResponse.class);

        assertThat(response.getCurrent().getTemperature2m()).isEqualTo(31.4);
        assertThat(response.getCurrent().getRelativeHumidity2m()).isEqualTo(72);
        assertThat(response.getCurrent().getWindSpeed10m()).isEqualTo(12.5);
        assertThat(response.getCurrent().getWindDirection10m()).isEqualTo(135);
        assertThat(response.getCurrent().getWindGusts10m()).isEqualTo(24.0);
        assertThat(response.getDaily().getTemperature2mMax()).containsExactly(34.0);
        assertThat(response.getDaily().getPrecipitationSum()).containsExactly(12.5);
        assertThat(response.getDaily().getWindGusts10mMax()).containsExactly(40.0);
        assertThat(response.getDaily().getUvIndexMax()).containsExactly(9.2);
    }

    @Test
    void deserializesAirQualityFields() throws Exception {
        String json = """
            {
              "timezone": "Asia/Ho_Chi_Minh",
              "current": {
                "time": "2026-07-31T14:00",
                "pm2_5": 157.9,
                "pm10": 167.2,
                "carbon_monoxide": 932.0,
                "nitrogen_dioxide": 5.2,
                "us_aqi": 149,
                "european_aqi": 88
              }
            }
            """;

        OpenMeteoAirQualityResponse response = new ObjectMapper()
            .readValue(json, OpenMeteoAirQualityResponse.class);

        assertThat(response.getCurrent().getPm25()).isEqualTo(157.9);
        assertThat(response.getCurrent().getPm10()).isEqualTo(167.2);
        assertThat(response.getCurrent().getCarbonMonoxide()).isEqualTo(932.0);
        assertThat(response.getCurrent().getUsAqi()).isEqualTo(149);
        assertThat(response.getCurrent().getEuropeanAqi()).isEqualTo(88);
    }
}
