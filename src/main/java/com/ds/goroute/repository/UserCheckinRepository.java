package com.ds.goroute.repository;

import com.ds.goroute.entity.CheckinCluster;
import com.ds.goroute.entity.CheckinClusterDecision;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinLocationHistory;
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

    /**
     * The author's own check-in for a given activity, place or trip, newest first.
     *
     * <p>At least one filter must be given by the caller; with none it would answer
     * "your last check-in anywhere", which is never the question being asked.
     */
    Optional<UserCheckin> findMine(UUID userId, UUID activityId, UUID placeId, UUID tripId);

    Optional<UserCheckin> findByIdempotencyKey(UUID userId, String idempotencyKey);

    List<UserCheckin> findFeed(UUID excludeUserId, int limit, int offset);

    List<UserCheckin> findByUser(UUID userId, boolean includePrivate, int limit, int offset);

    long countByUser(UUID userId, boolean includePrivate);

    List<UserCheckin> findByPlace(UUID placeId, int limit, int offset);

    long countByUserAndPlace(UUID userId, UUID placeId);

    List<UserCheckin> findLatestRatedPerUserForPlace(UUID placeId);

    List<UserCheckin> findByLocationKey(String locationKey, int limit, int offset);

    long countByUserAndLocationKey(UUID userId, String locationKey);

    List<UserCheckin> findLatestRatedPerUserForLocationKey(String locationKey);

    int markRemoved(UUID id, UUID userId);

    /** Soft-deleted check-ins that still own photo rows, bounded for background cleanup. */
    List<UUID> findRemovedCheckinIdsWithPhotos(int limit);

    int attachPlaceToCluster(String locationKey, UUID placeId);

    int attachReview(UUID id, UUID reviewId);

    /**
     * Clears the link from every check-in that pointed at a review which no longer exists,
     * so a deleted review cannot leave rows claiming to carry one.
     */
    int detachReview(UUID reviewId);

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

    // --- admin console ----------------------------------------------------------------

    /**
     * Every live check-in, newest first, for the admin console's own listing.
     *
     * @param hidden null for both, true for only the ones a takedown currently hides
     */
    List<UserCheckin> findAllForAdmin(String search, UUID userId, Boolean hidden, int limit, int offset);

    long countAllForAdmin(String search, UUID userId, Boolean hidden);

    /** Moves a check-in onto a catalogue place, keeping the author's own wording. */
    int assignPlace(UUID id, UUID placeId, String locationKey, String provinceCode);

    int insertLocationHistory(UserCheckinLocationHistory history);

    List<UserCheckinLocationHistory> findLocationHistory(UUID checkinId);

    List<UUID> findReassignedCheckinIds(List<UUID> checkinIds);
}
