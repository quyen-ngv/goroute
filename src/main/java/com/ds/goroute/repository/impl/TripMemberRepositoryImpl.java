package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripCount;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.mapper.TripMemberMapper;
import com.ds.goroute.repository.TripMemberRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripMemberRepositoryImpl implements TripMemberRepository {

    private static final int ID_BATCH_SIZE = 500;

    private final TripMemberMapper tripMemberMapper;
    
    @Override
    public void insert(TripMember member) {
        tripMemberMapper.insert(member);
    }
    
    @Override
    public Optional<TripMember> findById(UUID id) {
        return Optional.ofNullable(tripMemberMapper.selectById(id));
    }
    
    @Override
    public List<TripMember> findByTripId(UUID tripId) {
        return tripMemberMapper.selectByTripId(tripId);
    }
    
    @Override
    public List<TripMember> findByUserId(UUID userId) {
        return tripMemberMapper.selectByUserId(userId);
    }
    
    @Override
    public Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId) {
        return Optional.ofNullable(tripMemberMapper.selectByTripIdAndUserId(tripId, userId));
    }
    
    @Override
    public List<TripMember> findByTripIdsAndUserId(List<UUID> tripIds, UUID userId) {
        if (tripIds == null || tripIds.isEmpty() || userId == null) {
            return List.of();
        }
        return tripMemberMapper.selectByTripIdsAndUserId(tripIds, userId);
    }

    @Override
    public Map<UUID, Integer> countAcceptedByTripIds(Collection<UUID> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = tripIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, Integer> counts = new HashMap<>();
        for (List<UUID> batch : Lists.partition(distinct, ID_BATCH_SIZE)) {
            for (TripCount row : tripMemberMapper.countAcceptedByTripIds(batch)) {
                counts.put(row.getTripId(), row.getItemCount() == null ? 0 : row.getItemCount());
            }
        }
        return counts;
    }

    @Override
    public void updateById(TripMember member) {
        tripMemberMapper.updateById(member);
    }
    
    @Override
    public void deleteById(UUID id) {
        tripMemberMapper.deleteById(id);
    }
    
    @Override
    public void deleteByTripIdAndUserId(UUID tripId, UUID userId) {
        tripMemberMapper.deleteByTripIdAndUserId(tripId, userId);
    }
    
    @Override
    public List<TripMember> findPendingByUserId(UUID userId) {
        return tripMemberMapper.selectPendingByUserId(userId);
    }
    
    @Override
    public List<TripMember> findGuestsByEmail(String email) {
        return tripMemberMapper.selectGuestsByEmail(email);
    }
}
