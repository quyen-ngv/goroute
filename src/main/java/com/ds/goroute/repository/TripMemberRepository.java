package com.ds.goroute.repository;

import com.ds.goroute.entity.TripMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripMemberRepository {
    void insert(TripMember member);
    
    Optional<TripMember> findById(UUID id);
    
    List<TripMember> findByTripId(UUID tripId);
    
    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);
    
    void updateById(TripMember member);
    
    void deleteById(UUID id);
    
    void deleteByTripIdAndUserId(UUID tripId, UUID userId);

    List<TripMember> findByUserId(UUID userId);
    
    List<TripMember> findPendingByUserId(UUID userId);
    
    List<TripMember> findGuestsByEmail(String email);
}
