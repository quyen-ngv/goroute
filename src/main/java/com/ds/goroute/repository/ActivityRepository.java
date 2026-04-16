package com.ds.goroute.repository;

import com.ds.goroute.entity.Activity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository {
    void insert(Activity activity);
    
    Optional<Activity> findById(UUID id);
    
    List<Activity> findByTripId(UUID tripId);
    
    List<Activity> findByTripIdAndDayNumber(UUID tripId, int dayNumber);
    
    void updateById(Activity activity);
    
    void deleteById(UUID id);
    
    void deleteByTripId(UUID tripId);
}
