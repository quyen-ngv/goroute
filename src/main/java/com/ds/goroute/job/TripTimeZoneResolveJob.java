package com.ds.goroute.job;

import com.ds.goroute.service.time.TripTimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fills {@code trips.timezone} from the destination point for trips that have none: new trips
 * from any creation path, trips whose destination moved, and the backlog from before the column
 * was populated. Small batches keep the upstream lookup well under its free-tier rate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripTimeZoneResolveJob {

    private static final int BATCH_SIZE = 50;

    private final TripTimeZones tripTimeZones;

    @Scheduled(fixedDelayString = "${goroute.jobs.trip-timezone-resolve-delay-ms:30000}",
            initialDelayString = "${goroute.jobs.trip-timezone-resolve-initial-delay-ms:60000}")
    public void resolve() {
        try {
            int resolved = tripTimeZones.resolvePending(BATCH_SIZE);
            if (resolved > 0) {
                log.info("Resolved time zones of {} trips", resolved);
            }
        } catch (Exception exception) {
            log.error("Trip time zone resolve failed: {}", exception.getMessage(), exception);
        }
    }
}
