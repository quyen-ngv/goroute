package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.entity.UnlinkedActivityRow;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.repository.ActivityRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {
    
    private static final int ID_BATCH_SIZE = 500;

    private final ActivityMapper activityMapper;
    private final TripMapper tripMapper;

    @Override
    public void insert(Activity activity) {
        activityMapper.insert(activity);
        refreshTripActivityCount(activity.getTripId());
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
        activityMapper.updateById(activity);
    }
    
    @Override
    public void update(Activity activity) {
        activityMapper.updateById(activity);
    }
    
    @Override
    public List<Activity> findAll() {
        return activityMapper.selectAll();
    }
    
    @Override
    public void deleteById(UUID id) {
        UUID tripId = activityMapper.selectTripIdById(id);
        if (activityMapper.deleteById(id) > 0) {
            refreshTripActivityCount(tripId);
        }
    }

    @Override
    public void deleteByTripId(UUID tripId) {
        activityMapper.deleteByTripId(tripId);
        refreshTripActivityCount(tripId);
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
        return activityMapper.assignPlace(activityId, placeId.toString(), placeId, lat, lng);
    }
}
