package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A storytelling point one member heard (§3.14.2). {@code via} is GPS or TAP; only a GPS visit
 * inside the point's radius counts toward STOPS completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRunStopVisit {

    private UUID id;
    private UUID runId;
    private UUID memberId;
    private UUID checkpointId;
    private UUID stopId;
    private String via;
    private boolean countsTowardCompletion;
    private LocalDateTime visitedAt;
}
