package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripMember;
import com.ds.goroute.mapper.TripMemberMapper;
import com.ds.goroute.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripMemberRepositoryImpl implements TripMemberRepository {
    
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
