package com.ds.goroute.job;

import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.WeatherSnapshotProvider;
import com.ds.goroute.utils.GeoGridKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the weather and air-quality snapshot cache warm for curated locations.
 *
 * <p>Several location images can share one weather grid cell, so the job refreshes each
 * distinct cell once rather than making one pair of upstream calls per location image.
 * A failed cell is isolated so the remaining cities are still refreshed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherCacheRefreshJob {

    private final LocationImageRepository locationImageRepository;
    private final WeatherSnapshotProvider weatherSnapshotProvider;

    @Scheduled(fixedDelayString = "${goroute.jobs.weather-cache-refresh-delay-ms:900000}")
    public void refreshWeatherCache() {
        List<LocationImage> locations = locationImageRepository.findAll();
        Set<String> refreshedCells = new HashSet<>();
        int refreshed = 0;
        int failed = 0;

        for (LocationImage location : locations) {
            if (location.getLatitude() == null || location.getLongitude() == null) {
                continue;
            }

            String gridCell = GeoGridKey.of(location.getLatitude(), location.getLongitude());
            if (!refreshedCells.add(gridCell)) {
                continue;
            }

            try {
                weatherSnapshotProvider.refreshSnapshot(location.getLatitude(), location.getLongitude());
                refreshed++;
            } catch (RestClientException exception) {
                failed++;
                log.warn("Could not refresh weather cache for grid cell {}", gridCell, exception);
            }
        }

        log.info("Weather cache refresh completed: {} grid cells refreshed, {} failed",
            refreshed, failed);
    }
}
