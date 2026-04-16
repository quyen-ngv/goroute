package com.ds.goroute.repository;

import com.ds.goroute.entity.TripNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripNoteRepository {
    void insert(TripNote note);
    
    Optional<TripNote> findById(UUID id);
    
    List<TripNote> findByTripId(UUID tripId);
    
    void updateById(TripNote note);
    
    void deleteById(UUID id);
    
    void softDelete(UUID id);
}
