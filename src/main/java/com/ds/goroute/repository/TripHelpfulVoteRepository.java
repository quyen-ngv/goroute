package com.ds.goroute.repository;

import com.ds.goroute.entity.TripHelpfulVote;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface TripHelpfulVoteRepository {
    void save(TripHelpfulVote vote);

    void update(TripHelpfulVote vote);

    void delete(UUID tripId, UUID userId);

    TripHelpfulVote findByTripIdAndUserId(UUID tripId, UUID userId);

    /** The user's vote on each of the given trips, keyed by trip id; trips without one are absent. */
    Map<UUID, TripHelpfulVote> findByTripIdsAndUserId(Collection<UUID> tripIds, UUID userId);

    int countByTripIdAndIsHelpful(UUID tripId, boolean isHelpful);
}
