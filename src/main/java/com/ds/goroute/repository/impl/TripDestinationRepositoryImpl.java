package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.mapper.TripDestinationMapper;
import com.ds.goroute.repository.TripDestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripDestinationRepositoryImpl implements TripDestinationRepository {
    private final TripDestinationMapper tripDestinationMapper;

    @Override
    public void insert(TripDestination destination) {
        tripDestinationMapper.insert(destination);
    }

    @Override
    public List<TripDestination> findByTripId(UUID tripId) {
        return tripDestinationMapper.selectByTripId(tripId);
    }

    @Override
    public Optional<TripDestination> findById(UUID id) {
        return Optional.ofNullable(tripDestinationMapper.selectById(id));
    }

    @Override
    public void replaceForTrip(UUID tripId, List<TripDestination> destinations) {
        tripDestinationMapper.deleteByTripId(tripId);
        destinations.forEach(tripDestinationMapper::insert);
    }

    @Override
    public void deleteByTripId(UUID tripId) {
        tripDestinationMapper.deleteByTripId(tripId);
    }
}
