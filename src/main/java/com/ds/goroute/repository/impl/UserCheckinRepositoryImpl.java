package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.CheckinCluster;
import com.ds.goroute.entity.CheckinClusterDecision;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinLocationHistory;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.CheckinLikeCount;
import com.ds.goroute.mapper.UserCheckinMapper;
import com.ds.goroute.repository.UserCheckinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserCheckinRepositoryImpl implements UserCheckinRepository {

    private final UserCheckinMapper mapper;

    @Override
    public int insert(UserCheckin checkin) {
        return mapper.insert(checkin);
    }

    @Override
    public int update(UserCheckin checkin) {
        return mapper.update(checkin);
    }

    @Override
    public Optional<UserCheckin> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<UserCheckin> findMine(UUID userId, UUID activityId, UUID placeId, UUID tripId) {
        return Optional.ofNullable(mapper.findMine(userId, activityId, placeId, tripId));
    }

    @Override
    public Optional<UserCheckin> findByIdempotencyKey(UUID userId, String idempotencyKey) {
        return Optional.ofNullable(mapper.findByIdempotencyKey(userId, idempotencyKey));
    }

    @Override
    public List<UserCheckin> findFeed(UUID excludeUserId, int limit, int offset) {
        return mapper.findFeed(excludeUserId, limit, offset);
    }

    @Override
    public List<UserCheckin> findByUser(UUID userId, boolean includePrivate, int limit, int offset) {
        return mapper.findByUser(userId, includePrivate, limit, offset);
    }

    @Override
    public long countByUser(UUID userId, boolean includePrivate) {
        return mapper.countByUser(userId, includePrivate);
    }

    @Override
    public List<UserCheckin> findByPlace(UUID placeId, int limit, int offset) {
        return mapper.findByPlace(placeId, limit, offset);
    }

    @Override
    public long countByUserAndPlace(UUID userId, UUID placeId) {
        return mapper.countByUserAndPlace(userId, placeId);
    }

    @Override
    public List<UserCheckin> findLatestRatedPerUserForPlace(UUID placeId) {
        return mapper.findLatestRatedPerUserForPlace(placeId);
    }

    @Override
    public List<UserCheckin> findByLocationKey(String locationKey, int limit, int offset) {
        return mapper.findByLocationKey(locationKey, limit, offset);
    }

    @Override
    public long countByUserAndLocationKey(UUID userId, String locationKey) {
        return mapper.countByUserAndLocationKey(userId, locationKey);
    }

    @Override
    public List<UserCheckin> findByLocationKeyAndPlace(String locationKey, UUID placeId) {
        return mapper.findByLocationKeyAndPlace(locationKey, placeId);
    }

    @Override
    public List<UserCheckin> findLatestRatedPerUserForLocationKey(String locationKey) {
        return mapper.findLatestRatedPerUserForLocationKey(locationKey);
    }

    @Override
    public int markRemoved(UUID id, UUID userId) {
        return mapper.markRemoved(id, userId);
    }

    @Override
    public List<UUID> findRemovedCheckinIdsWithPhotos(int limit) {
        return mapper.findRemovedCheckinIdsWithPhotos(limit);
    }

    @Override
    public int attachPlaceToCluster(String locationKey, UUID placeId) {
        return mapper.attachPlaceToCluster(locationKey, placeId);
    }

    @Override
    public int attachReview(UUID id, UUID reviewId) {
        return mapper.attachReview(id, reviewId);
    }

    @Override
    public int detachReview(UUID reviewId) {
        return mapper.detachReview(reviewId);
    }

    @Override
    public int recordReward(UUID id, int rewardPoints, String rewardReason) {
        return mapper.recordReward(id, rewardPoints, rewardReason);
    }

    @Override
    public int sumRewardPointsSince(UUID userId, LocalDateTime since) {
        Integer total = mapper.sumRewardPointsSince(userId, since);
        return total == null ? 0 : total;
    }

    @Override
    public int insertPhoto(UserCheckinPhoto photo) {
        return mapper.insertPhoto(photo);
    }

    @Override
    public List<UserCheckinPhoto> findPhotos(UUID checkinId) {
        return mapper.findPhotos(checkinId);
    }

    @Override
    public List<UserCheckinPhoto> findPhotosForCheckins(List<UUID> checkinIds) {
        return checkinIds.isEmpty() ? List.of() : mapper.findPhotosForCheckins(checkinIds);
    }

    @Override
    public int deletePhotos(UUID checkinId) {
        return mapper.deletePhotos(checkinId);
    }

    @Override
    public boolean hasLike(UUID checkinId, UUID userId) {
        return mapper.hasLike(checkinId, userId);
    }

    @Override
    public int insertLike(UUID checkinId, UUID userId) {
        return mapper.insertLike(checkinId, userId);
    }

    @Override
    public int deleteLike(UUID checkinId, UUID userId) {
        return mapper.deleteLike(checkinId, userId);
    }

    @Override
    public int countLikes(UUID checkinId) {
        return mapper.countLikes(checkinId);
    }

    @Override
    public List<CheckinLikeCount> findLikeCounts(List<UUID> checkinIds) {
        return checkinIds == null || checkinIds.isEmpty() ? List.of() : mapper.findLikeCounts(checkinIds);
    }

    @Override
    public List<UUID> findLikedCheckinIds(UUID userId, List<UUID> checkinIds) {
        return userId == null || checkinIds == null || checkinIds.isEmpty()
                ? List.of() : mapper.findLikedCheckinIds(userId, checkinIds);
    }

    @Override
    public List<CheckinCluster> findClusters(int minUsers, int minCheckins, int limit, int offset) {
        return mapper.findClusters(minUsers, minCheckins, limit, offset);
    }

    @Override
    public long countClusters(int minUsers, int minCheckins) {
        return mapper.countClusters(minUsers, minCheckins);
    }

    @Override
    public Optional<CheckinCluster> findCluster(String locationKey) {
        return Optional.ofNullable(mapper.findCluster(locationKey));
    }

    @Override
    public int upsertClusterDecision(CheckinClusterDecision decision) {
        return mapper.upsertClusterDecision(decision);
    }

    @Override
    public Optional<CheckinClusterDecision> findClusterDecision(String locationKey) {
        return Optional.ofNullable(mapper.findClusterDecision(locationKey));
    }

    @Override
    public int deleteClusterDecision(String locationKey) {
        return mapper.deleteClusterDecision(locationKey);
    }

    @Override
    public List<UserCheckin> findWithoutPassportEvent(int limit) {
        return mapper.findWithoutPassportEvent(limit);
    }

    @Override
    public List<String> findVisitedProvinceCodes(UUID userId) {
        return mapper.findVisitedProvinceCodes(userId);
    }

    @Override
    public List<UserCheckin> findAllForAdmin(String search, UUID userId, Boolean hidden, int limit, int offset) {
        return mapper.findAllForAdmin(search, userId, hidden, limit, offset);
    }

    @Override
    public long countAllForAdmin(String search, UUID userId, Boolean hidden) {
        return mapper.countAllForAdmin(search, userId, hidden);
    }

    @Override
    public int assignPlace(UUID id, UUID placeId, String locationKey, String provinceCode) {
        return mapper.assignPlace(id, placeId, locationKey, provinceCode);
    }

    @Override
    public int insertLocationHistory(UserCheckinLocationHistory history) {
        return mapper.insertLocationHistory(history);
    }

    @Override
    public List<UserCheckinLocationHistory> findLocationHistory(UUID checkinId) {
        return mapper.findLocationHistory(checkinId);
    }

    @Override
    public List<UUID> findReassignedCheckinIds(List<UUID> checkinIds) {
        return checkinIds == null || checkinIds.isEmpty() ? List.of() : mapper.findReassignedCheckinIds(checkinIds);
    }

    @Override
    public int updateVerification(UserCheckin checkin) {
        return mapper.updateVerification(checkin);
    }

    @Override
    public List<com.ds.goroute.dto.response.VisitedWardResponse> findVisitedWards(UUID userId, boolean includePrivate) {
        return mapper.findVisitedWards(userId, includePrivate);
    }
}
