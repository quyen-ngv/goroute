package com.ds.goroute.service;

import com.ds.goroute.dto.response.CityWeatherResponse;

import java.util.UUID;

public interface CityWeatherService {
    CityWeatherResponse getCurrentWeather(UUID locationId);
}
