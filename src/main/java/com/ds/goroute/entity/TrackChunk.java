package com.ds.goroute.entity;

import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One uploaded batch of raw GPS points for one local day ({@code trip_track_chunks}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackChunk {
    private UUID id;
    private UUID tripId;
    private UUID userId;
    private UUID clientChunkId;
    private UUID clientSessionId;
    private LocalDate localDate;
    /** UTC. */
    private LocalDateTime startedAt;
    /** UTC. */
    private LocalDateTime endedAt;
    private Integer pointCount;
    private Integer rejectedCount;
    private TrackAccuracyMode accuracyMode;
    private TrackSource source;
    /** Columnar points JSON ({@code TrackPointsPayload}). */
    private String points;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long dataVersion;
}
