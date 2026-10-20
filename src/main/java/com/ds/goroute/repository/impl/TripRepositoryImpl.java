package com.ds.goroute.repository.impl;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripRankRow;
import com.ds.goroute.event.PlaceUsageChangedEvent;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripRepositoryImpl implements TripRepository {

    private static final int ID_BATCH_SIZE = 500;

    private final TripMapper tripMapper;
    private final ActivityRepository activityRepository;
    private final ApplicationEventPublisher events;
    
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
        // A deleted trip no longer counts towards its places' usage (V225).
        Set<String> places = activityRepository.tripPlaceReferences(id);
        tripMapper.deleteById(id);
        if (!places.isEmpty()) {
            events.publishEvent(new PlaceUsageChangedEvent(places));
        }
    }
    
    @Override
    public Optional<Trip> findByShareCode(String shareCode) {
        return Optional.ofNullable(tripMapper.selectByShareCode(shareCode));
    }
    
    @Override
    public List<TripRankRow> searchPublicTripRanking(PublicTripSearchCriteria criteria,
                                                     UUID excludeUserId,
                                                     int limit,
                                                     int offset) {
        return tripMapper.searchPublicTripRanking(criteria, excludeUserId, limit, offset);
    }

    @Override
    public List<Trip> findPublicByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trip> trips = new ArrayList<>();
        for (List<UUID> batch : Lists.partition(ids, ID_BATCH_SIZE)) {
            trips.addAll(tripMapper.selectPublicByIds(batch));
        }
        return trips;
    }

    @Override
    public boolean claimFollowersNotification(UUID tripId) {
        return tripId != null && tripMapper.claimFollowersNotification(tripId) == 1;
    }

    @Override
    public void refreshActivityCount(UUID tripId) {
        if (tripId != null) {
            tripMapper.refreshActivityCount(tripId);
        }
    }

    @Override
    public void refreshMemoryCount(UUID tripId) {
        if (tripId != null) {
            tripMapper.refreshMemoryCount(tripId);
        }
    }

    @Override
    public int reconcileContentCounts() {
        return tripMapper.reconcileContentCounts();
    }
    
    @Override
    public List<Trip> findFollowingTrips(UUID viewerId, Double focusLat, Double focusLng,
                                         int limit, int offset) {
        return tripMapper.findFollowingTrips(viewerId, focusLat, focusLng, limit, offset);
    }

    @Override
    public List<UUID> findPublicTripIdsByPlace(UUID placeId, int limit, int offset) {
        return tripMapper.selectPublicTripIdsByPlace(placeId, limit, offset);
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

    @Override
    public int sumCopyCountByOwnerId(UUID ownerId) {
        return tripMapper.sumCopyCountByOwnerId(ownerId);
    }

    @Override
    public int sumHelpfulVotesByOwnerId(UUID ownerId) {
        return tripMapper.sumHelpfulVotesByOwnerId(ownerId);
    }

    @Override
    public List<Trip> findNotificationCandidates(LocalDate fromDate, LocalDate toDate) {
        return tripMapper.selectNotificationCandidates(fromDate, toDate);
    }
}
