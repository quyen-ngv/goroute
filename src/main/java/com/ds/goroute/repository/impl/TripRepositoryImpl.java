package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
    
    @Override
    public List<Trip> searchPublicTrips(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm, String destination, int page, int size, UUID excludeUserId) {
        int offset = page * size;
        return tripMapper.searchPublicTrips(latitude, longitude, radiusKm, destination, offset, size, excludeUserId);
    }
    
    @Override
    public void incrementViewCount(UUID id) {
        tripMapper.incrementViewCount(id);
    }
    
    @Override
    public void incrementCopyCount(UUID id) {
        tripMapper.incrementCopyCount(id);
    }

    @Override
    public void updateVoteCounts(Trip trip) {
        tripMapper.updateVoteCounts(trip);
    }
    
    @Override
    public Optional<Trip> findMostRecentByUserId(UUID userId) {
        return Optional.ofNullable(tripMapper.selectMostRecentByUserId(userId));
    }

    @Override
    public List<Trip> findPublicTripsByOwnerId(UUID ownerId) {
        return tripMapper.selectPublicTripsByOwnerId(ownerId);
    }

    @Override
    public int countPublicTripsByOwnerId(UUID ownerId) {
        return tripMapper.countPublicTripsByOwnerId(ownerId);
    }

    @Override
    public int countTripsByOwnerId(UUID ownerId) {
        return tripMapper.countTripsByOwnerId(ownerId);
    }
}
