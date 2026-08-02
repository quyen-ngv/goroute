package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.thirdparty.weather.OpenMeteoClient;
import com.ds.goroute.thirdparty.weather.OpenMeteoWeatherResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityWeatherServiceImplTest {

    @Mock
    private LocationImageRepository locationImageRepository;

    @Mock
    private OpenMeteoClient openMeteoClient;

    @InjectMocks
    private CityWeatherServiceImpl service;

    @Test
    void returnsCurrentWeatherForLocationCoordinates() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = LocationImage.builder()
            .id(locationId)
            .fullAddress("Đà Nẵng")
            .citySlug("danang")
            .latitude(new BigDecimal("16.05440000"))
            .longitude(new BigDecimal("108.20220000"))
            .build();

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
        current.setIsDay(1);
        OpenMeteoWeatherResponse upstream = new OpenMeteoWeatherResponse("Asia/Bangkok", current);

        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(openMeteoClient.getCurrentWeather(location.getLatitude(), location.getLongitude()))
            .thenReturn(upstream);

        CityWeatherResponse result = service.getCurrentWeather(locationId);

        assertThat(result.getLocationId()).isEqualTo(locationId);
        assertThat(result.getTemperatureC()).isEqualTo(31.4);
        assertThat(result.getCondition()).isEqualTo("Rain");
        assertThat(result.getDay()).isTrue();
        assertThat(result.getProvider()).isEqualTo("Open-Meteo");
    }

    @Test
    void rejectsLocationWithoutCoordinates() {
        UUID locationId = UUID.randomUUID();
        LocationImage location = LocationImage.builder().id(locationId).fullAddress("Unknown").build();
        when(locationImageRepository.findById(locationId)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> service.getCurrentWeather(locationId))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Latitude and longitude are required to retrieve city weather");
        verifyNoInteractions(openMeteoClient);
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
                "wind_direction_10m": 135
              }
            }
            """;

        OpenMeteoWeatherResponse response = new ObjectMapper()
            .readValue(json, OpenMeteoWeatherResponse.class);

        assertThat(response.getCurrent().getTemperature2m()).isEqualTo(31.4);
        assertThat(response.getCurrent().getRelativeHumidity2m()).isEqualTo(72);
        assertThat(response.getCurrent().getWindSpeed10m()).isEqualTo(12.5);
        assertThat(response.getCurrent().getWindDirection10m()).isEqualTo(135);
    }
}
