package com.ds.goroute.repository;

import com.ds.goroute.entity.Checkin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckinRepository {
    void insert(Checkin checkin);
    
    Optional<Checkin> findById(UUID id);
    
    List<Checkin> findByActivityId(UUID activityId);
    
    List<Checkin> findByTripId(UUID tripId);
    
    List<Checkin> findByUserId(UUID userId);
    
    Optional<Checkin> findByActivityIdAndUserId(UUID activityId, UUID userId);
    
    void updateById(Checkin checkin);
    
    void deleteById(UUID id);
}
