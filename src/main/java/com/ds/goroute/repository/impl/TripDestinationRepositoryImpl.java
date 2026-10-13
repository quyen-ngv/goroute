package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.mapper.TripDestinationMapper;
import com.ds.goroute.repository.TripDestinationRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripDestinationRepositoryImpl implements TripDestinationRepository {
    private static final int ID_BATCH_SIZE = 500;

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
    public Map<UUID, List<TripDestination>> findByTripIds(Collection<UUID> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = tripIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, List<TripDestination>> byTrip = new LinkedHashMap<>();
        for (List<UUID> batch : Lists.partition(distinct, ID_BATCH_SIZE)) {
            for (TripDestination destination : tripDestinationMapper.selectByTripIds(batch)) {
                byTrip.computeIfAbsent(destination.getTripId(), ignored -> new ArrayList<>()).add(destination);
            }
        }
        return byTrip;
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

    @Override
    public void updateDates(UUID id, LocalDate startDate, LocalDate endDate) {
        tripDestinationMapper.updateDates(id, startDate, endDate);
    }
}
