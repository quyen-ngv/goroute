package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One location sample the client posts while walking toward a checkpoint (§3.8). */
public record QuestSampleRequest(
        UUID checkpointId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        LocalDateTime capturedAt,
        boolean gpsOverride) {
}
