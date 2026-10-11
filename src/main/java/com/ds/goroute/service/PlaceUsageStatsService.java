package com.ds.goroute.service;

import java.util.Collection;

/**
 * Keeps {@code places.trip_use_count} and {@code places.checkin_count} (V225), the usage Home's
 * top places and the trip suggestions rank by. Not in the request: writes mark the places they
 * touch, {@link #recountMarked()} recounts those in batches, and {@link #recountAll()} repairs
 * every place once a night. Each recount is set-based and idempotent, so a place marked twice
 * or recounted with nothing changed costs nothing but the query.
 */
public interface PlaceUsageStatsService {

    /**
     * Marks the places named by {@code placeReferences} (catalogue ids or Google place ids) for
     * the next recount. Cheap and non-blocking; unknown references are dropped at recount time.
     */
    void markChanged(Collection<String> placeReferences);

    /** Recounts the places marked so far; returns how many changed. */
    int recountMarked();

    /** Recounts every place; returns how many changed. */
    int recountAll();
}
