package com.ds.goroute.repository;

import com.ds.goroute.entity.TripDestination;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TripDestinationRepository {
    void insert(TripDestination destination);

    List<TripDestination> findByTripId(UUID tripId);

    /** Destinations of a page of trips in one query, keyed by trip id; trips without any are absent. */
    Map<UUID, List<TripDestination>> findByTripIds(Collection<UUID> tripIds);

    Optional<TripDestination> findById(UUID id);

    void replaceForTrip(UUID tripId, List<TripDestination> destinations);

    void deleteByTripId(UUID tripId);

    /** Changes one destination's date range only, keeping everything else on the row. */
    void updateDates(UUID id, LocalDate startDate, LocalDate endDate);
}
