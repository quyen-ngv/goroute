package com.ds.goroute.quest.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Where the player stands, for a hot/cold question (§3.14.1). */
public record QuestProximityRequest(
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        BigDecimal accuracyMeters) {
}
