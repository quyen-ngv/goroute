package com.ds.goroute.mapper;

import com.ds.goroute.entity.CheckinCluster;
import com.ds.goroute.entity.CheckinClusterDecision;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.CheckinLikeCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserCheckinMapper {

    int insert(UserCheckin checkin);

    int update(UserCheckin checkin);

    UserCheckin findById(@Param("id") UUID id);

    /** Resolves a repeated submit of the same composition back to the row it created. */
    UserCheckin findMine(@Param("userId") UUID userId,
                         @Param("activityId") UUID activityId,
                         @Param("placeId") UUID placeId,
                         @Param("tripId") UUID tripId);

    UserCheckin findByIdempotencyKey(@Param("userId") UUID userId,
                                     @Param("idempotencyKey") String idempotencyKey);

    /** Public feed, newest first, keyed on the last item seen rather than a page number. */
    List<UserCheckin> findFeed(@Param("before") LocalDateTime before,
                               @Param("excludeUserId") UUID excludeUserId,
                               @Param("limit") int limit);

    List<UserCheckin> findByUser(@Param("userId") UUID userId,
                                 @Param("includePrivate") boolean includePrivate,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    long countByUser(@Param("userId") UUID userId, @Param("includePrivate") boolean includePrivate);

    List<UserCheckin> findByPlace(@Param("placeId") UUID placeId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    long countByUserAndPlace(@Param("userId") UUID userId,
                             @Param("placeId") UUID placeId);

    /** The check-in currently backing each author's one review of this place. */
    List<UserCheckin> findLatestRatedPerUserForPlace(@Param("placeId") UUID placeId);

    List<UserCheckin> findByLocationKey(@Param("locationKey") String locationKey,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    /** Counts this author's check-ins in a location cluster without a public-feed limit. */
    long countByUserAndLocationKey(@Param("userId") UUID userId,
                                   @Param("locationKey") String locationKey);

    /**
     * The user's most recent rating at one spot. Used when a cluster is promoted: a person
     * can have rated the same spot several times, and a review is one per person, so the
     * most recent opinion is the one that becomes the review.
     */
    List<UserCheckin> findLatestRatedPerUserForLocationKey(@Param("locationKey") String locationKey);

    int markRemoved(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Soft-deleted check-ins whose photo rows have not been purged yet.
     * The cleanup job consumes this bounded queue in oldest-removal order.
     */
    List<UUID> findRemovedCheckinIdsWithPhotos(@Param("limit") int limit);

    int attachPlaceToCluster(@Param("locationKey") String locationKey, @Param("placeId") UUID placeId);

    int attachReview(@Param("id") UUID id, @Param("reviewId") UUID reviewId);

    int detachReview(@Param("reviewId") UUID reviewId);

    int recordReward(@Param("id") UUID id,
                     @Param("rewardPoints") int rewardPoints,
                     @Param("rewardReason") String rewardReason);

    /** Points already granted today, for the daily cap. */
    Integer sumRewardPointsSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    int insertPhoto(UserCheckinPhoto photo);

    List<UserCheckinPhoto> findPhotos(@Param("checkinId") UUID checkinId);

    List<UserCheckinPhoto> findPhotosForCheckins(@Param("checkinIds") List<UUID> checkinIds);

    int deletePhotos(@Param("checkinId") UUID checkinId);

    boolean hasLike(@Param("checkinId") UUID checkinId, @Param("userId") UUID userId);

    int insertLike(@Param("checkinId") UUID checkinId, @Param("userId") UUID userId);

    int deleteLike(@Param("checkinId") UUID checkinId, @Param("userId") UUID userId);

    int countLikes(@Param("checkinId") UUID checkinId);

    List<CheckinLikeCount> findLikeCounts(@Param("checkinIds") List<UUID> checkinIds);

    List<UUID> findLikedCheckinIds(@Param("userId") UUID userId,
                                   @Param("checkinIds") List<UUID> checkinIds);

    // --- CHK-12: the promotion queue -------------------------------------------------

    List<CheckinCluster> findClusters(@Param("minUsers") int minUsers,
                                      @Param("minCheckins") int minCheckins,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    long countClusters(@Param("minUsers") int minUsers, @Param("minCheckins") int minCheckins);

    CheckinCluster findCluster(@Param("locationKey") String locationKey);

    int upsertClusterDecision(CheckinClusterDecision decision);

    CheckinClusterDecision findClusterDecision(@Param("locationKey") String locationKey);

    int deleteClusterDecision(@Param("locationKey") String locationKey);

    // --- PAS-02: the projection into passport events ---------------------------------

    /** Check-ins that have not produced a passport event yet, oldest first. */
    List<UserCheckin> findWithoutPassportEvent(@Param("limit") int limit);

    /** Distinct provinces this user has a verified or unverified presence in. */
    List<String> findVisitedProvinceCodes(@Param("userId") UUID userId);
}
