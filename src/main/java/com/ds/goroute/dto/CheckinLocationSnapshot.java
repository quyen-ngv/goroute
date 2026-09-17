package com.ds.goroute.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The location a check-in carried before an operator moved it to a catalogue place.
 * Serialized into {@code user_checkin_location_history.previous_location}.
 */
public record CheckinLocationSnapshot(
        UUID placeId,
        String locationName,
        String customName,
        BigDecimal latitude,
        BigDecimal longitude,
        String ward,
        String district,
        String province,
        String provinceCode,
        String locationSource,
        String locationKey) {
}
