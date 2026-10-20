package com.ds.goroute.repository;

import com.ds.goroute.dto.FeedCursor;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
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

    List<UserReview> findByIds(List<UUID> ids);

    Optional<UserReview> findByUserAndPlace(UUID userId, UUID placeId);

    Optional<UserReview> findByUserAndActivityBooking(UUID userId, UUID activityBookingId);

    Optional<UserReview> findByHotelBookingId(UUID hotelBookingId);

    Optional<UserReview> findByActivityOrderId(UUID activityOrderId);

    List<UserReview> findByPlaceId(UUID placeId, int limit, int offset);

    List<UserReview> findByActivityBookingId(UUID activityBookingId, int limit, int offset);

    List<UserReview> findByUserId(UUID userId, int limit, int offset);

    /**
     * The Feed's reviews. {@code after} resumes after a review's sort key (keyset paging) and
     * is ignored when {@code randomSeed} shuffles the order, which pages by {@code offset}.
     */
    List<UserReview> findFeedReviews(UUID excludeUserId, FeedGeoFilter geo, FeedPlaceKind kind, FeedCursor after,
                                     int limit, int offset, String randomSeed);

    int countByPlaceId(UUID placeId);

    int countByActivityBookingId(UUID activityBookingId);

    int countByUserId(UUID userId);

    int sumHelpfulVotesByUserId(UUID userId);

    int countByUserInTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime);

    void delete(UUID id);
    
    void deleteByIds(List<UUID> ids);
}
