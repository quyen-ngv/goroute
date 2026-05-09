package com.ds.goroute.repository;

import com.ds.goroute.entity.UserReview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReviewRepository {
    
    void save(UserReview review);
    
    void update(UserReview review);
    
    Optional<UserReview> findById(UUID id);
    
    Optional<UserReview> findByUserAndPlace(UUID userId, UUID placeId);
    
    List<UserReview> findByPlaceId(UUID placeId, int limit, int offset);
    
    List<UserReview> findByUserId(UUID userId, int limit, int offset);
    
    int countByPlaceId(UUID placeId);
    
    int countByUserId(UUID userId);
    
    int countByUserInTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime);
    
    void delete(UUID id);
}
