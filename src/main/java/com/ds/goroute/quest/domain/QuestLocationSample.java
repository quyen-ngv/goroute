package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A raw GPS sample (§7.6). Retained briefly then purged; reading it needs quests:location-data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestLocationSample {

    private UUID id;
    private UUID runId;
    private UUID memberId;
    private UUID checkpointId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;
    private LocalDateTime capturedAt;
    private LocalDateTime createdAt;
}
