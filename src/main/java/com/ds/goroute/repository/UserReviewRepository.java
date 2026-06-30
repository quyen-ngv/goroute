package com.ds.goroute.repository;

import com.ds.goroute.entity.UserReview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReviewRepository {

    void save(UserReview review);

    void update(UserReview review);

    void updateVoteCounts(UserReview review);

    Optional<UserReview> findById(UUID id);

    Optional<UserReview> findByUserAndPlace(UUID userId, UUID placeId);

    Optional<UserReview> findByUserAndActivityBooking(UUID userId, UUID activityBookingId);

    List<UserReview> findByPlaceId(UUID placeId, int limit, int offset);

    List<UserReview> findByActivityBookingId(UUID activityBookingId, int limit, int offset);

    List<UserReview> findByUserId(UUID userId, int limit, int offset);

    List<UserReview> findFeedReviews(UUID excludeUserId, int limit, int offset);

    int countByPlaceId(UUID placeId);

    int countByActivityBookingId(UUID activityBookingId);

    int countByUserId(UUID userId);

    int countByUserInTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime);

    void delete(UUID id);
    
    void deleteByIds(List<UUID> ids);
}
