package com.ds.goroute.repository;

import com.ds.goroute.entity.TripDestination;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripDestinationRepository {
    void insert(TripDestination destination);

    List<TripDestination> findByTripId(UUID tripId);

    Optional<TripDestination> findById(UUID id);

    void replaceForTrip(UUID tripId, List<TripDestination> destinations);

    void deleteByTripId(UUID tripId);
}
