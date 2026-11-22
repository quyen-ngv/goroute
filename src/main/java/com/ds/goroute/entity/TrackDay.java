package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Statistics and simplified line of one person's route on one local day of a trip
 * ({@code trip_track_days}), rebuilt from the raw chunks after every change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackDay {
    private UUID id;
    private UUID tripId;
    private UUID userId;
    private LocalDate localDate;
    /** {@code localDate - trip.startDate + 1}; 0 or N+1 on the padding days around a trip. */
    private Integer dayNumber;
    /** JSON array of {@code {"kind": ..., "polyline": ...}}. */
    private String lineSegments;
    /** JSON {@code [minLng, minLat, maxLng, maxLat]} or null. */
    private String bbox;
    private Integer distanceM;
    private Integer flightDistanceM;
    private Integer movingSeconds;
    private Integer elevationGainM;
    private Integer elevationLossM;
    private Integer pointCount;
    /** JSON array of province codes the route passes through (V203); {@code []} when none. */
    private String provinceCodes;
    /** UTC. */
    private LocalDateTime firstAt;
    /** UTC. */
    private LocalDateTime lastAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long dataVersion;
}
