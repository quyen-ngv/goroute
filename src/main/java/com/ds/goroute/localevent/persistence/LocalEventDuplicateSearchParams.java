package com.ds.goroute.localevent.persistence;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fuzzy duplicate detection on import: other live events starting in the window, within the
 * distance (bounding box + haversine) or in the same province, whose normalised title is at
 * least {@code minSimilarity} similar (pg_trgm).
 */
@Getter
@Builder
public class LocalEventDuplicateSearchParams {
    private final UUID excludeId;
    private final String titleNorm;
    private final double minSimilarity;
    private final LocalDateTime startFrom;
    private final LocalDateTime startTo;
    private final double latitude;
    private final double longitude;
    private final double radiusKm;
    private final double minLat;
    private final double maxLat;
    private final double minLng;
    private final double maxLng;
    /** Null when the event's province is unknown; then only the distance counts. */
    private final String provinceCode;
    private final int limit;
}
