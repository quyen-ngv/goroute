package com.ds.goroute.quest.dto;

import java.math.BigDecimal;

/**
 * A storytelling point was heard (§3.14.2). With a position it is a GPS visit, which counts toward
 * STOPS when inside the point's radius; without one it is a tap, recorded but never counted.
 */
public record QuestStopVisitRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters) {
}
