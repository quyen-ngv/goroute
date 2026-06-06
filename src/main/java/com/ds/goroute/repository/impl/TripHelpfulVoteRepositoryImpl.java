package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripHelpfulVote;
import com.ds.goroute.mapper.TripHelpfulVoteMapper;
import com.ds.goroute.repository.TripHelpfulVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripHelpfulVoteRepositoryImpl implements TripHelpfulVoteRepository {
    private final TripHelpfulVoteMapper mapper;

    @Override
    public void save(TripHelpfulVote vote) {
        mapper.insert(vote);
    }

    @Override
    public void update(TripHelpfulVote vote) {
        mapper.update(vote);
    }

    @Override
    public void delete(UUID tripId, UUID userId) {
        mapper.delete(tripId, userId);
    }

    @Override
    public TripHelpfulVote findByTripIdAndUserId(UUID tripId, UUID userId) {
        return mapper.findByTripIdAndUserId(tripId, userId);
    }

    @Override
    public int countByTripIdAndIsHelpful(UUID tripId, boolean isHelpful) {
        return mapper.countByTripIdAndIsHelpful(tripId, isHelpful);
    }
}
