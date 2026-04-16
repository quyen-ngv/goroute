package com.ds.goroute.repository;

import com.ds.goroute.entity.Trip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {
    void insert(Trip trip);
    
    Optional<Trip> findById(UUID id);
    
    List<Trip> findByOwnerId(UUID ownerId);
    
    List<Trip> findByUserId(UUID userId);
    
    void updateById(Trip trip);
    
    void deleteById(UUID id);
    
    Optional<Trip> findByShareCode(String shareCode);
}
