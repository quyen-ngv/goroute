package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.service.CityWeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Weather anywhere, by coordinate.
 *
 * <p>{@code /location-images/{id}/weather} only answers for the curated cities. A trip runs
 * through ordinary places across several provinces, so its itinerary needs the reading for
 * wherever the traveller happens to be that day, which is a coordinate and not a catalogue
 * entry. Both endpoints share the grid-cell snapshot cache, so this adds no upstream cost
 * for a stop that sits in a city already being asked about.
 */
@RestController
@RequestMapping("/v1/api/weather")
@RequiredArgsConstructor
@Slf4j
public class WeatherController extends BaseController {

    private final CityWeatherService cityWeatherService;

    @GetMapping
    public ResponseEntity<BaseResponse<CityWeatherResponse>> getWeather(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng) {
        return ResponseEntity.ok(ofSucceeded(cityWeatherService.getCurrentWeather(lat, lng)));
    }
}
