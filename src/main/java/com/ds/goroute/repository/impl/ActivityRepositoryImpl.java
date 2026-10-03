package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.UnlinkedActivityRow;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {
    
    private final ActivityMapper activityMapper;
    
    @Override
    public void insert(Activity activity) {
        activityMapper.insert(activity);
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
        activityMapper.deleteById(id);
    }
    
    @Override
    public void deleteByTripId(UUID tripId) {
        activityMapper.deleteByTripId(tripId);
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
