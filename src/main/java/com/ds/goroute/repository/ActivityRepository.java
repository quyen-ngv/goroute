package com.ds.goroute.repository;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.UnlinkedActivityRow;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Activities of a trip. Every insert and delete here also recounts the owning trip's
 * {@code trips.activity_count} (V216), the ranking input of public trip search, and every
 * write that may change which places the trip uses publishes a {@code PlaceUsageChangedEvent}
 * (V225 usage counts), so no caller has to remember either.
 */
public interface ActivityRepository {
    void insert(Activity activity);
    
    Optional<Activity> findById(UUID id);
    
    List<Activity> findAll();
    
    List<Activity> findByTripId(UUID tripId);
    
    /** Number of activities on a trip, for list payloads that do not carry the activities themselves. */
    int countByTripId(UUID tripId);

    /** Activity count per trip for a page of trips, in one grouped query; absent trips have 0. */
    Map<UUID, Integer> countByTripIds(Collection<UUID> tripIds);

    /** Names of the given activities, keyed by id; unnamed or missing activities are absent. */
    Map<UUID, String> findNamesByIds(Collection<UUID> activityIds);

    List<Activity> findByTripIdAndDayNumber(UUID tripId, int dayNumber);
    
    void updateById(Activity activity);
    
    void update(Activity activity); // Alias for updateById
    
    void deleteById(UUID id);
    
    void deleteByTripId(UUID tripId);

    /**
     * The place references (place_ref_id, place_id) of the trip's activities: what a change
     * to the whole trip, such as deleting it, may change the usage counts of (V225).
     */
    Set<String> tripPlaceReferences(UUID tripId);

    /**
     * Activities with an address that resolves to no catalogue place: still in the admin queue,
     * newest first, or ({@code dismissed}) taken out of it, most recently dismissed first.
     */
    List<UnlinkedActivityRow> findUnlinkedForAdmin(String search, boolean dismissed, int limit, int offset);

    long countUnlinkedForAdmin(String search, boolean dismissed);

    /** Takes the activity out of the admin queue; 0 when it is missing or already out. */
    int dismissPlaceReview(UUID activityId, UUID adminId);

    /** Puts a dismissed activity back in the admin queue; 0 when it is missing or not dismissed. */
    int restorePlaceReview(UUID activityId);

    /** Points the activity at a catalogue place; returns the number of rows changed. */
    int assignPlace(UUID activityId, UUID placeId, BigDecimal lat, BigDecimal lng);

    /**
     * Unlinks the trip's activity from any catalogue place and puts it at a map place's point,
     * with that place's Mapbox id (null clears it). Returns the number of rows changed.
     */
    int assignMapPlace(UUID tripId, UUID activityId, BigDecimal lat, BigDecimal lng, String mapboxId);

    /** Records the Mapbox place an activity of the trip was made from. Returns the rows changed. */
    int setMapboxId(UUID tripId, UUID activityId, String mapboxId);

    /**
     * Renumbers the trip's activities from {@code fromDay} on by {@code dayDelta}; a starting
     * point's date moves by {@code dateDeltaDays}. Returns the number of activities moved.
     */
    int shiftDays(UUID tripId, int fromDay, int dayDelta, int dateDeltaDays);

    /** Exchanges the activities of two days of the trip. Returns the number of activities moved. */
    int swapDays(UUID tripId, int firstDay, int secondDay);
}
