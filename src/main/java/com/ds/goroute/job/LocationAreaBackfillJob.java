package com.ds.goroute.job;

import com.ds.goroute.dto.response.LocationAreaAutoMapResponse;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.LocationAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Keeps the tourist-area column filled for rows created after the last run.
 *
 * <p>Places arrive continuously from the scraper and partners create tours and hotels on
 * their own schedule, so without this the area would only be correct immediately after an
 * operator remembered to press the button in the console - and "which area is this in?"
 * would silently start returning null for everything recent.
 *
 * <p>Only rows with no area are touched, so an operator's manual correction is never
 * overwritten and an interrupted run simply resumes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocationAreaBackfillJob {

    private final LocationAreaService locationAreaService;
    private final GeoService geoService;

    @Scheduled(fixedDelayString = "${goroute.jobs.location-area-backfill-delay-ms:3600000}",
            initialDelayString = "${goroute.jobs.location-area-backfill-initial-delay-ms:120000}")
    public void backfill() {
        LocationAreaAutoMapResponse result = locationAreaService.autoMap(false);
        int assigned = result.getTargets().stream()
                .mapToInt(target -> target.getByCoordinates() + target.getByName())
                .sum();
        if (assigned > 0) {
            log.info("Resolved a tourist area for {} rows: {}", assigned, result.getTargets());
        }

        // The administrative half of the same question, on the same schedule rather than in
        // a job of its own: both are derived columns that go stale for the same reason, and
        // a second @Scheduled bean is a second thing to double-run on a second instance.
        Map<String, Long> wards = geoService.assignPendingWards();
        long wardRows = wards.values().stream().mapToLong(Long::longValue).sum();
        if (wardRows > 0) {
            log.info("Resolved a ward for {} rows: {}", wardRows, wards);
        }
    }
}
