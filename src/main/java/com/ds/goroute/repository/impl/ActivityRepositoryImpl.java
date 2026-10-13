package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.entity.UnlinkedActivityRow;
import com.ds.goroute.event.PlaceUsageChangedEvent;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.repository.ActivityRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {
    
    private static final int ID_BATCH_SIZE = 500;

    private final ActivityMapper activityMapper;
    private final TripMapper tripMapper;
    private final ApplicationEventPublisher events;

    @Override
    public void insert(Activity activity) {
        activityMapper.insert(activity);
        refreshTripActivityCount(activity.getTripId());
        publishPlaceUsageChange(placeReferences(activity));
    }
    
    @Override
    public Optional<Activity> findById(UUID id) {
        return Optional.ofNullable(activityMapper.selectById(id));
    }
    
    @Override
    public List<Activity> findByTripId(UUID tripId) {
        return activityMapper.selectByTripId(tripId);
    }
    
    @Override
    public int countByTripId(UUID tripId) {
        return activityMapper.countByTripId(tripId);
    }

    @Override
    public Map<UUID, Integer> countByTripIds(Collection<UUID> tripIds) {
        List<UUID> distinct = distinctIds(tripIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (List<UUID> batch : Lists.partition(distinct, ID_BATCH_SIZE)) {
            for (TripCount row : activityMapper.countByTripIds(batch)) {
                counts.put(row.getTripId(), row.getItemCount() == null ? 0 : row.getItemCount());
            }
        }
        return counts;
    }

    @Override
    public Map<UUID, String> findNamesByIds(Collection<UUID> activityIds) {
        List<UUID> distinct = distinctIds(activityIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (List<UUID> batch : Lists.partition(distinct, ID_BATCH_SIZE)) {
            for (Activity activity : activityMapper.selectNamesByIds(batch)) {
                if (activity.getName() != null) {
                    names.put(activity.getId(), activity.getName());
                }
            }
        }
        return names;
    }

    private static List<UUID> distinctIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void refreshTripActivityCount(UUID tripId) {
        if (tripId != null) {
            tripMapper.refreshActivityCount(tripId);
        }
    }
    
    @Override
    public List<Activity> findByTripIdAndDayNumber(UUID tripId, int dayNumber) {
        return activityMapper.selectByTripIdAndDayNumber(tripId, dayNumber);
    }
    
    @Override
    public void updateById(Activity activity) {
        Activity before = activity.getId() == null ? null : activityMapper.selectById(activity.getId());
        activityMapper.updateById(activity);
        publishUsageChangeOfUpdate(before, activity);
    }

    @Override
    public void update(Activity activity) {
        updateById(activity);
    }

    /**
     * An update writes only the fields it carries: a place it does not carry stays the old one.
     * Usage changes when the place or the status (REJECTED does not count) does.
     */
    private void publishUsageChangeOfUpdate(Activity before, Activity update) {
        if (before == null) {
            return;
        }
        boolean placeChanged = (update.getPlaceId() != null && !update.getPlaceId().equals(before.getPlaceId()))
                || (update.getPlaceRefId() != null && !update.getPlaceRefId().equals(before.getPlaceRefId()));
        boolean statusChanged = update.getStatus() != null && update.getStatus() != before.getStatus();
        if (!placeChanged && !statusChanged) {
            return;
        }
        Set<String> references = placeReferences(before);
        references.addAll(placeReferences(update));
        publishPlaceUsageChange(references);
    }
    
    @Override
    public List<Activity> findAll() {
        return activityMapper.selectAll();
    }
    
    @Override
    public void deleteById(UUID id) {
        Activity before = activityMapper.selectById(id);
        if (activityMapper.deleteById(id) > 0 && before != null) {
            refreshTripActivityCount(before.getTripId());
            publishPlaceUsageChange(placeReferences(before));
        }
    }

    @Override
    public void deleteByTripId(UUID tripId) {
        Set<String> references = tripPlaceReferences(tripId);
        activityMapper.deleteByTripId(tripId);
        refreshTripActivityCount(tripId);
        publishPlaceUsageChange(references);
    }

    @Override
    public Set<String> tripPlaceReferences(UUID tripId) {
        Set<String> references = new HashSet<>();
        if (tripId == null) {
            return references;
        }
        for (Activity activity : activityMapper.selectPlaceReferencesByTripId(tripId)) {
            references.addAll(placeReferences(activity));
        }
        return references;
    }

    /** The loose references an activity names its place by: place_ref_id, place_id. */
    private static Set<String> placeReferences(Activity activity) {
        Set<String> references = new HashSet<>();
        if (activity == null) {
            return references;
        }
        if (activity.getPlaceRefId() != null) {
            references.add(activity.getPlaceRefId().toString());
        }
        if (activity.getPlaceId() != null && !activity.getPlaceId().isBlank()) {
            references.add(activity.getPlaceId().trim());
        }
        return references;
    }

    private void publishPlaceUsageChange(Set<String> references) {
        if (!references.isEmpty()) {
            events.publishEvent(new PlaceUsageChangedEvent(references));
        }
    }

    @Override
    public List<UnlinkedActivityRow> findUnlinkedForAdmin(String search, int limit, int offset) {
        return activityMapper.selectUnlinkedForAdmin(search, limit, offset);
    }

    @Override
    public long countUnlinkedForAdmin(String search) {
        return activityMapper.countUnlinkedForAdmin(search);
    }

    @Override
    public int assignPlace(UUID activityId, UUID placeId, BigDecimal lat, BigDecimal lng) {
        Activity before = activityMapper.selectById(activityId);
        int changed = activityMapper.assignPlace(activityId, placeId.toString(), placeId, lat, lng);
        if (changed > 0) {
            Set<String> references = placeReferences(before);
            references.add(placeId.toString());
            publishPlaceUsageChange(references);
        }
        return changed;
    }

    @Override
    public int shiftDays(UUID tripId, int fromDay, int dayDelta, int dateDeltaDays) {
        return activityMapper.shiftDays(tripId, fromDay, dayDelta, dateDeltaDays);
    }

    @Override
    public int swapDays(UUID tripId, int firstDay, int secondDay) {
        return activityMapper.swapDays(tripId, firstDay, secondDay);
    }
}
