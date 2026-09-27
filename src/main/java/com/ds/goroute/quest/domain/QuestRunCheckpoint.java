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
    /** Hot/cold (§3.14.1): last distance to the real spot. Server-only, never sent to the app. */
    private BigDecimal lastProximityM;
    private LocalDateTime lastProximityAt;
    private String proximityBand;
    private String proximityTrend;
    /** AR_OBJECT (§3.15): the tap that found the object, and how it was anchored on the phone. */
    private LocalDateTime arTappedAt;
    private BigDecimal arTapLat;
    private BigDecimal arTapLng;
    private BigDecimal arTapAccuracy;
    private String arAnchorMode;

    public boolean isUnlocked() {
        return unlockedAt != null;
    }
}
