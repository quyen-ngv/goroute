package com.ds.goroute.service;

import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.LocationImage;

import java.math.BigDecimal;
import java.util.UUID;

public interface CityWeatherService {

    /**
     * Weather for a curated location image, as its own endpoint.
     *
     * @throws com.ds.goroute.exception.BusinessException 404 when the location is unknown,
     *                                                    400 when it has no coordinates,
     *                                                    502 when the provider is unavailable
     */
    CityWeatherResponse getCurrentWeather(UUID locationId);

    /**
     * Weather for a free coordinate, for stops that are not curated location images.
     *
     * <p>A trip moves between provinces and its stops are ordinary places rather than
     * catalogued cities, so an itinerary has to ask for weather by where it actually is.
     * The snapshot cache is keyed by grid cell rather than by location, so this shares
     * upstream calls with any curated city falling in the same cell.
     *
     * @throws com.ds.goroute.exception.BusinessException 400 when the coordinate is out of range,
     *                                                    502 when the provider is unavailable
     */
    CityWeatherResponse getCurrentWeather(BigDecimal latitude, BigDecimal longitude);

    /**
     * Weather to embed next to a location image.
     *
     * <p>Returns {@code null} instead of throwing when the location has no coordinates or
     * the provider is down, so an outage never takes the location image down with it.
     */
    CityWeatherResponse findWeatherQuietly(LocationImage location);
}
