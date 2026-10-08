package com.ds.goroute.repository;

import com.ds.goroute.entity.TripMember;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TripMemberRepository {
    void insert(TripMember member);
    
    Optional<TripMember> findById(UUID id);
    
    List<TripMember> findByTripId(UUID tripId);
    
    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    /** One user's member rows across several trips, keyed by trip id by the caller. */
    List<TripMember> findByTripIdsAndUserId(List<UUID> tripIds, UUID userId);

    /** ACCEPTED member count per trip for a page of trips, in one grouped query; absent trips have 0. */
    Map<UUID, Integer> countAcceptedByTripIds(Collection<UUID> tripIds);

    void updateById(TripMember member);
    
    void deleteById(UUID id);
    
    void deleteByTripIdAndUserId(UUID tripId, UUID userId);

    List<TripMember> findByUserId(UUID userId);
    
    List<TripMember> findPendingByUserId(UUID userId);
    
    List<TripMember> findGuestsByEmail(String email);
}
