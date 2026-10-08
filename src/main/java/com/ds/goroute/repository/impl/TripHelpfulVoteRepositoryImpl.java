package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripHelpfulVote;
import com.ds.goroute.mapper.TripHelpfulVoteMapper;
import com.ds.goroute.repository.TripHelpfulVoteRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripHelpfulVoteRepositoryImpl implements TripHelpfulVoteRepository {
    private static final int ID_BATCH_SIZE = 500;

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
    public Map<UUID, TripHelpfulVote> findByTripIdsAndUserId(Collection<UUID> tripIds, UUID userId) {
        if (userId == null || tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = tripIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, TripHelpfulVote> votes = new HashMap<>();
        for (List<UUID> batch : Lists.partition(distinct, ID_BATCH_SIZE)) {
            for (TripHelpfulVote vote : mapper.findByTripIdsAndUserId(batch, userId)) {
                votes.put(vote.getTripId(), vote);
            }
        }
        return votes;
    }

    @Override
    public int countByTripIdAndIsHelpful(UUID tripId, boolean isHelpful) {
        return mapper.countByTripIdAndIsHelpful(tripId, isHelpful);
    }
}
