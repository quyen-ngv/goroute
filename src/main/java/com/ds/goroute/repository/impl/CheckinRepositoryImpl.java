package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Checkin;
import com.ds.goroute.mapper.CheckinMapper;
import com.ds.goroute.repository.CheckinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CheckinRepositoryImpl implements CheckinRepository {
    
    private final CheckinMapper checkinMapper;
    
    @Override
    public void insert(Checkin checkin) {
        checkinMapper.insert(checkin);
    }
    
    @Override
    public Optional<Checkin> findById(UUID id) {
        return Optional.ofNullable(checkinMapper.selectById(id));
    }
    
    @Override
    public List<Checkin> findByActivityId(UUID activityId) {
        return checkinMapper.selectByActivityId(activityId);
    }
    
    @Override
    public List<Checkin> findByTripId(UUID tripId) {
        return checkinMapper.selectByTripId(tripId);
    }
    
    @Override
    public List<Checkin> findByUserId(UUID userId) {
        return checkinMapper.selectByUserId(userId);
    }
    
    @Override
    public Optional<Checkin> findByActivityIdAndUserId(UUID activityId, UUID userId) {
        return Optional.ofNullable(checkinMapper.selectByActivityIdAndUserId(activityId, userId));
    }
    
    @Override
    public void updateById(Checkin checkin) {
        checkinMapper.updateById(checkin);
    }
    
    @Override
    public void deleteById(UUID id) {
        checkinMapper.deleteById(id);
    }
}
