package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A member's progress at one checkpoint (§6.2). Arrival is decided server-side from a streak. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRunCheckpoint {

    private UUID id;
    private UUID runId;
    private UUID memberId;
    private UUID checkpointId;
    private boolean gpsOverride;
    private BigDecimal distanceMeters;
    private BigDecimal accuracyMeters;
    private Integer stableStreak;
    private LocalDateTime lastSampleAt;
    private BigDecimal lastSampleLat;
    private BigDecimal lastSampleLng;
    private UUID lastSampleId;
    private LocalDateTime unlockedAt;
    private UUID checkinId;
    private String checkinState;
    private Long dataVersion;

    public boolean isUnlocked() {
        return unlockedAt != null;
    }
}
