package com.ds.goroute.repository;

import com.ds.goroute.entity.Trip;
import java.math.BigDecimal;
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
    
    List<Trip> searchPublicTrips(BigDecimal latitude,
                                 BigDecimal longitude,
                                 BigDecimal radiusKm,
                                 String destination,
                                 String keyword,
                                 boolean allPublic,
                                 String randomSeed,
                                 int page,
                                 int size,
                                 UUID excludeUserId);
    
    void incrementViewCount(UUID id);
    
    void incrementCopyCount(UUID id);

    void updateVoteCounts(Trip trip);

    Optional<Trip> findMostRecentByUserId(UUID userId);

    List<Trip> findPublicTripsByOwnerId(UUID ownerId);

    int countPublicTripsByOwnerId(UUID ownerId);

    int countTripsByOwnerId(UUID ownerId);

    int sumCopyCountByOwnerId(UUID ownerId);

    int sumHelpfulVotesByOwnerId(UUID ownerId);
}
