package com.ds.goroute.repository;

import com.ds.goroute.entity.CheckinCluster;
import com.ds.goroute.entity.CheckinClusterDecision;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.CheckinLikeCount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for check-ins, their photos and the promotion queue (epic 06). */
public interface UserCheckinRepository {

    int insert(UserCheckin checkin);

    int update(UserCheckin checkin);

    Optional<UserCheckin> findById(UUID id);

    Optional<UserCheckin> findByIdempotencyKey(UUID userId, String idempotencyKey);

    List<UserCheckin> findFeed(LocalDateTime before, UUID excludeUserId, int limit);

    List<UserCheckin> findByUser(UUID userId, boolean includePrivate, int limit, int offset);

    long countByUser(UUID userId, boolean includePrivate);

    List<UserCheckin> findByPlace(UUID placeId, int limit, int offset);

    List<UserCheckin> findLatestRatedPerUserForPlace(UUID placeId);

    List<UserCheckin> findByLocationKey(String locationKey, int limit, int offset);

    List<UserCheckin> findLatestRatedPerUserForLocationKey(String locationKey);

    int markRemoved(UUID id, UUID userId);

    int attachPlaceToCluster(String locationKey, UUID placeId);

    int attachReview(UUID id, UUID reviewId);

    int recordReward(UUID id, int rewardPoints, String rewardReason);

    int sumRewardPointsSince(UUID userId, LocalDateTime since);

    int insertPhoto(UserCheckinPhoto photo);

    List<UserCheckinPhoto> findPhotos(UUID checkinId);

    List<UserCheckinPhoto> findPhotosForCheckins(List<UUID> checkinIds);

    int deletePhotos(UUID checkinId);

    boolean hasLike(UUID checkinId, UUID userId);

    int insertLike(UUID checkinId, UUID userId);

    int deleteLike(UUID checkinId, UUID userId);

    int countLikes(UUID checkinId);

    List<CheckinLikeCount> findLikeCounts(List<UUID> checkinIds);

    List<UUID> findLikedCheckinIds(UUID userId, List<UUID> checkinIds);

    List<CheckinCluster> findClusters(int minUsers, int minCheckins, int limit, int offset);

    long countClusters(int minUsers, int minCheckins);

    Optional<CheckinCluster> findCluster(String locationKey);

    int upsertClusterDecision(CheckinClusterDecision decision);

    Optional<CheckinClusterDecision> findClusterDecision(String locationKey);

    int deleteClusterDecision(String locationKey);

    List<UserCheckin> findWithoutPassportEvent(int limit);

    List<String> findVisitedProvinceCodes(UUID userId);
}
