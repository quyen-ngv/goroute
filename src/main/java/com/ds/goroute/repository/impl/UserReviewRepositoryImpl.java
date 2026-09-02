package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserReview;
import com.ds.goroute.mapper.UserReviewMapper;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserReviewRepositoryImpl implements UserReviewRepository {

    private final UserReviewMapper mapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Override
    public void save(UserReview review) {
        enforceManagedPhotos(review);
        mapper.insert(review);
    }

    @Override
    public void update(UserReview review) {
        enforceManagedPhotos(review);
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
    public List<UserReview> findByIds(List<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of() : mapper.findByIds(ids);
    }

    @Override
    public Optional<UserReview> findByUserAndPlace(UUID userId, UUID placeId) {
        return Optional.ofNullable(mapper.findByUserAndPlace(userId, placeId));
    }

    @Override
    public Optional<UserReview> findByUserAndActivityBooking(UUID userId, UUID activityBookingId) {
        return Optional.ofNullable(mapper.findByUserAndActivityBooking(userId, activityBookingId));
    }

    @Override
    public Optional<UserReview> findByHotelBookingId(UUID hotelBookingId) {
        return Optional.ofNullable(mapper.findByHotelBookingId(hotelBookingId));
    }

    @Override
    public Optional<UserReview> findByActivityOrderId(UUID activityOrderId) {
        return Optional.ofNullable(mapper.findByActivityOrderId(activityOrderId));
    }

    @Override
    public List<UserReview> findByPlaceId(UUID placeId, int limit, int offset) {
        return mapper.findByPlaceId(placeId, limit, offset);
    }

    @Override
    public List<UserReview> findByActivityBookingId(UUID activityBookingId, int limit, int offset) {
        return mapper.findByActivityBookingId(activityBookingId, limit, offset);
    }

    @Override
    public List<UserReview> findByUserId(UUID userId, int limit, int offset) {
        return mapper.findByUserId(userId, limit, offset);
    }

    @Override
    public List<UserReview> findFeedReviews(UUID excludeUserId, int limit, int offset, String randomSeed) {
        return mapper.findFeedReviews(excludeUserId, limit, offset, randomSeed);
    }

    @Override
    public int countByPlaceId(UUID placeId) {
        return mapper.countByPlaceId(placeId);
    }

    @Override
    public int countByActivityBookingId(UUID activityBookingId) {
        return mapper.countByActivityBookingId(activityBookingId);
    }

    @Override
    public int countByUserId(UUID userId) {
        return mapper.countByUserId(userId);
    }

    @Override
    public int sumHelpfulVotesByUserId(UUID userId) {
        return mapper.sumHelpfulVotesByUserId(userId);
    }

    @Override
    public int countByUserInTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        return mapper.countByUserInTimeRange(userId, startTime, endTime);
    }

    @Override
    public void delete(UUID id) {
        mapper.delete(id);
    }
    
    @Override
    public void deleteByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mapper.deleteByIds(ids);
    }

    private void enforceManagedPhotos(UserReview review) {
        if (review == null || review.getPhotos() == null || review.getPhotos().isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(review.getPhotos());
            if (!root.isArray()) {
                review.setPhotos("[]");
                return;
            }
            List<String> managed = new java.util.ArrayList<>();
            root.forEach(node -> {
                if (node.isTextual()
                        && storageService.extractObjectKey(node.asText()) != null
                        && !managed.contains(node.asText())) {
                    managed.add(node.asText());
                }
            });
            review.setPhotos(objectMapper.writeValueAsString(managed));
        } catch (Exception e) {
            log.warn("Discarding malformed or unmanaged photos for user review {}: {}",
                    review.getId(), e.getMessage(), e);
            review.setPhotos("[]");
        }
    }
}
