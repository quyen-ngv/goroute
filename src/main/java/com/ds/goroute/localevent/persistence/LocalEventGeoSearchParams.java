package com.ds.goroute.localevent.persistence;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public radius search: a bounding box prefilter (indexed) narrowed by haversine, over a time
 * window that overlaps the event span. Times are UTC.
 */
@Getter
@Builder
public class LocalEventGeoSearchParams {
    private final double latitude;
    private final double longitude;
    private final double radiusKm;
    private final double minLat;
    private final double maxLat;
    private final double minLng;
    private final double maxLng;
    private final LocalDateTime from;
    private final LocalDateTime to;
    /** Category codes; empty means every category. */
    private final List<String> categories;
    private final int limit;
    private final int offset;
}
