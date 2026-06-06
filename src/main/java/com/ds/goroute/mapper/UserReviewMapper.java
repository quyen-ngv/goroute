package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserReviewMapper {

    void insert(UserReview review);

    void update(UserReview review);

    void updateVoteCounts(UserReview review);

    UserReview findById(@Param("id") UUID id);

    UserReview findByUserAndPlace(@Param("userId") UUID userId, @Param("placeId") UUID placeId);

    UserReview findByUserAndActivityBooking(@Param("userId") UUID userId,
                                            @Param("activityBookingId") UUID activityBookingId);

    List<UserReview> findByPlaceId(@Param("placeId") UUID placeId,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    List<UserReview> findByActivityBookingId(@Param("activityBookingId") UUID activityBookingId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    List<UserReview> findByUserId(@Param("userId") UUID userId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    List<UserReview> findFeedReviews(@Param("excludeUserId") UUID excludeUserId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    int countByPlaceId(@Param("placeId") UUID placeId);

    int countByActivityBookingId(@Param("activityBookingId") UUID activityBookingId);

    int countByUserId(@Param("userId") UUID userId);

    int countByUserInTimeRange(@Param("userId") UUID userId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    void delete(@Param("id") UUID id);
}
