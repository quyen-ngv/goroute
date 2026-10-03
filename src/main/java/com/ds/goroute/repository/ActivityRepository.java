package com.ds.goroute.repository;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.UnlinkedActivityRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository {
    void insert(Activity activity);
    
    Optional<Activity> findById(UUID id);
    
    List<Activity> findAll();
    
    List<Activity> findByTripId(UUID tripId);
    
    /** Number of activities on a trip, for list payloads that do not carry the activities themselves. */
    int countByTripId(UUID tripId);
    
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
