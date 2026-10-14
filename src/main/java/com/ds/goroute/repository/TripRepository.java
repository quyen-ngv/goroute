package com.ds.goroute.repository;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripRankRow;
import java.time.LocalDate;
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
    
    /**
     * Ranked public trips (id and owner) for one slice of a search, without deleted or
     * taken-down trips and, when {@code excludeUserId} is set, without the trips that user
     * owns or is an accepted member of.
     */
    List<TripRankRow> searchPublicTripRanking(PublicTripSearchCriteria criteria,
                                              UUID excludeUserId,
                                              int limit,
                                              int offset);

    /** The trips among {@code ids} that are still public, live and not taken down. */
    List<Trip> findPublicByIds(List<UUID> ids);

    /** Recounts {@code trips.activity_count} (V216) for one trip. */
    void refreshActivityCount(UUID tripId);

    /** Recounts {@code trips.memory_count} (V216) for one trip. */
    void refreshMemoryCount(UUID tripId);

    /** Repairs both V216 counters for every trip that drifted; returns the rows fixed. */
    int reconcileContentCounts();

    /**
     * Public trips owned by the people {@code viewerId} follows, most recently shared first,
     * without deleted owners, taken-down trips or blocks in either direction.
     */
    List<Trip> findFollowingTrips(UUID viewerId, Double focusLat, Double focusLng, int limit, int offset);

    /**
     * Ids of the public trips that have the catalogue place {@code placeId} in their
     * itinerary, most recently shared first.
     */
    List<UUID> findPublicTripIdsByPlace(UUID placeId, int limit, int offset);

    void incrementViewCount(UUID id);
    
    void incrementCopyCount(UUID id);

    void updateVoteCounts(Trip trip);

    Optional<Trip> findMostRecentByUserId(UUID userId);

    List<Trip> findPublicTripsByOwnerId(UUID ownerId);

    int countPublicTripsByOwnerId(UUID ownerId);

    int countTripsByOwnerId(UUID ownerId);

    int sumCopyCountByOwnerId(UUID ownerId);

    int sumHelpfulVotesByOwnerId(UUID ownerId);

    List<Trip> findNotificationCandidates(LocalDate fromDate, LocalDate toDate);
}
