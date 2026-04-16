package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ActivityComment;
import com.ds.goroute.mapper.ActivityCommentMapper;
import com.ds.goroute.repository.ActivityCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityCommentRepositoryImpl implements ActivityCommentRepository {
    
    private final ActivityCommentMapper activityCommentMapper;
    
    @Override
    public void insert(ActivityComment comment) {
        activityCommentMapper.insert(comment);
    }
    
    @Override
    public Optional<ActivityComment> findById(UUID id) {
        return Optional.ofNullable(activityCommentMapper.selectById(id));
    }
    
    @Override
    public List<ActivityComment> findByActivityId(UUID activityId) {
        return activityCommentMapper.selectByActivityId(activityId);
    }
    
    @Override
    public void updateById(ActivityComment comment) {
        activityCommentMapper.updateById(comment);
    }
    
    @Override
    public void deleteById(UUID id) {
        activityCommentMapper.deleteById(id);
    }
    
    @Override
    public void softDelete(UUID id) {
        activityCommentMapper.softDelete(id);
    }
}
