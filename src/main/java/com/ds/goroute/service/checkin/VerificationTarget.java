package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A self-describing point-of-interest for {@link CheckinVerifier#assess}, so the verifier no
 * longer needs a {@code places} row to reach a {@code PLACE} verdict.
 *
 * <p>Check-in builds one from a {@link Place}: the ward fallback is allowed, and the place's
 * drawn verification area is consulted before the radius. A quest checkpoint pins its own
 * coordinates on the map, has no {@code places} row (D2), and never accepts a ward-level
 * fallback (§3.8) &mdash; it builds one from bare coordinates with the ward flag off, so
 * {@code scopeIfLiveCapture()} can only ever return {@code PLACE} or {@code NONE}.
 */
public record VerificationTarget(
        BigDecimal latitude,
        BigDecimal longitude,
        int effectiveRadiusMeters,
        boolean allowWardFallback,
        /**
         * When set, the drawn verification area of this place is consulted before the radius.
         * Null for map-pinned targets, which fall straight to the radius.
         */
        UUID geometryPlaceId) {

    /** A target at a catalogue place: ward fallback allowed, drawn area consulted. */
    public static VerificationTarget forPlace(Place place, int effectiveRadiusMeters) {
        if (place == null) {
            return new VerificationTarget(null, null, effectiveRadiusMeters, true, null);
        }
        return new VerificationTarget(place.getLatitude(), place.getLongitude(),
                effectiveRadiusMeters, true, place.getId());
    }

    /** A target at bare coordinates: radius only, no ward fallback. Used by quest checkpoints. */
    public static VerificationTarget forCoordinates(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        return new VerificationTarget(latitude, longitude, radiusMeters, false, null);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
