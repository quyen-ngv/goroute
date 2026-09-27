package com.ds.goroute.quest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The player tapped the checkpoint's AR object (§3.15). The position is where the phone was; the
 * server checks it is within reach of the object. {@code anchorMode} is how the phone placed it:
 * APPROX, IMAGE, or FALLBACK (the 3D view on a device without AR).
 */
public record QuestArTapRequest(@NotNull BigDecimal latitude, @NotNull BigDecimal longitude,
                                BigDecimal accuracyMeters, @Size(max = 10) String anchorMode) {
}
