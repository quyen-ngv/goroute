package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<Activity> findByTripIdAndDayNumber(UUID tripId, int dayNumber) {
        return activityMapper.selectByTripIdAndDayNumber(tripId, dayNumber);
    }
    
    @Override
    public void updateById(Activity activity) {
        activityMapper.updateById(activity);
    }
    
    @Override
    public void deleteById(UUID id) {
        activityMapper.deleteById(id);
    }
    
    @Override
    public void deleteByTripId(UUID tripId) {
        activityMapper.deleteByTripId(tripId);
    }
}
