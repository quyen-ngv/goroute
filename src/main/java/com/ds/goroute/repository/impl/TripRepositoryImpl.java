package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripRepositoryImpl implements TripRepository {
    
    private final TripMapper tripMapper;
    
    @Override
    public void insert(Trip trip) {
        tripMapper.insert(trip);
    }
    
    @Override
    public Optional<Trip> findById(UUID id) {
        return Optional.ofNullable(tripMapper.selectById(id));
    }
    
    @Override
    public List<Trip> findByOwnerId(UUID ownerId) {
        return tripMapper.selectByOwnerId(ownerId);
    }
    
    @Override
    public List<Trip> findByUserId(UUID userId) {
        return tripMapper.selectByUserId(userId);
    }
    
    @Override
    public void updateById(Trip trip) {
        tripMapper.updateById(trip);
    }
    
    @Override
    public void deleteById(UUID id) {
        tripMapper.deleteById(id);
    }
    
    @Override
    public Optional<Trip> findByShareCode(String shareCode) {
        return Optional.ofNullable(tripMapper.selectByShareCode(shareCode));
    }
}
