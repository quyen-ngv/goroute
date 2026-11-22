package com.ds.goroute.event;

import java.util.Set;

/**
 * Published when a write may change how much travellers use some places: an activity added
 * to, moved within or removed from a trip, a trip deleted, a check-in attached to a place.
 * The places' {@code trip_use_count} and {@code checkin_count} (V225) are recounted after the
 * commit, a little later ({@code PlaceUsageStatsService}).
 *
 * <p>{@code placeReferences} are loose: an activity's {@code place_ref_id} or {@code place_id},
 * which holds a catalogue id or a Google place id. References to no catalogue place are
 * ignored.
 */
public record PlaceUsageChangedEvent(Set<String> placeReferences) {

    public PlaceUsageChangedEvent {
        placeReferences = placeReferences == null ? Set.of() : Set.copyOf(placeReferences);
    }
}
