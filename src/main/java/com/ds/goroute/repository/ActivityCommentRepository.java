package com.ds.goroute.repository;

import com.ds.goroute.entity.ActivityComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityCommentRepository {
    void insert(ActivityComment comment);
    
    Optional<ActivityComment> findById(UUID id);
    
    List<ActivityComment> findByActivityId(UUID activityId);
    
    void updateById(ActivityComment comment);
    
    void deleteById(UUID id);
    
    void softDelete(UUID id);
}
