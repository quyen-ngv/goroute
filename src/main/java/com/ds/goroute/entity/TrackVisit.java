package com.ds.goroute.entity;

import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A stop derived from a recorded route ({@code trip_track_visits}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackVisit {
    private UUID id;
    private UUID tripId;
    private UUID userId;
    private LocalDate localDate;
    private TrackVisitKind kind;
    private UUID activityId;
    private UUID placeId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** UTC. */
    private LocalDateTime arrivedAt;
    /** UTC; null while the person may still be there. */
    private LocalDateTime departedAt;
    private TrackVisitStatus status;
    private UUID createdActivityId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long dataVersion;
}
