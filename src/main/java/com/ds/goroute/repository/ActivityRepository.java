package com.ds.goroute.repository;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.UnlinkedActivityRow;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Activities of a trip. Every insert and delete here also recounts the owning trip's
 * {@code trips.activity_count} (V216), the ranking input of public trip search, so no
 * caller has to remember it.
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

    /** Activities with an address that resolves to no catalogue place, newest first. */
    List<UnlinkedActivityRow> findUnlinkedForAdmin(String search, int limit, int offset);

    long countUnlinkedForAdmin(String search);

    /** Points the activity at a catalogue place; returns the number of rows changed. */
    int assignPlace(UUID activityId, UUID placeId, BigDecimal lat, BigDecimal lng);
}
