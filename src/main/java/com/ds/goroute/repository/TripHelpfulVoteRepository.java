package com.ds.goroute.repository;

import com.ds.goroute.entity.TripHelpfulVote;

import java.util.UUID;

public interface TripHelpfulVoteRepository {
    void save(TripHelpfulVote vote);

    void update(TripHelpfulVote vote);

    void delete(UUID tripId, UUID userId);

    TripHelpfulVote findByTripIdAndUserId(UUID tripId, UUID userId);

    int countByTripIdAndIsHelpful(UUID tripId, boolean isHelpful);
}
