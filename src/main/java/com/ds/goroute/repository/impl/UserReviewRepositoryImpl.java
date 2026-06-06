package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserReview;
import com.ds.goroute.mapper.UserReviewMapper;
import com.ds.goroute.repository.UserReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserReviewRepositoryImpl implements UserReviewRepository {

    private final UserReviewMapper mapper;

    @Override
    public void save(UserReview review) {
        mapper.insert(review);
    }

    @Override
    public void update(UserReview review) {
        mapper.update(review);
    }

    @Override
    public void updateVoteCounts(UserReview review) {
        mapper.updateVoteCounts(review);
    }

    @Override
    public Optional<UserReview> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<UserReview> findByUserAndPlace(UUID userId, UUID placeId) {
        return Optional.ofNullable(mapper.findByUserAndPlace(userId, placeId));
    }

    @Override
    public List<UserReview> findByPlaceId(UUID placeId, int limit, int offset) {
        return mapper.findByPlaceId(placeId, limit, offset);
    }

    @Override
    public List<UserReview> findByUserId(UUID userId, int limit, int offset) {
        return mapper.findByUserId(userId, limit, offset);
    }

    @Override
    public List<UserReview> findFeedReviews(UUID excludeUserId, int limit, int offset) {
        return mapper.findFeedReviews(excludeUserId, limit, offset);
    }

    @Override
    public int countByPlaceId(UUID placeId) {
        return mapper.countByPlaceId(placeId);
    }

    @Override
    public int countByUserId(UUID userId) {
        return mapper.countByUserId(userId);
    }

    @Override
    public int countByUserInTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        return mapper.countByUserInTimeRange(userId, startTime, endTime);
    }

    @Override
    public void delete(UUID id) {
        mapper.delete(id);
    }
}
