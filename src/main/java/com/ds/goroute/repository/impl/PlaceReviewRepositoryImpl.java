package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.dto.response.PlaceReviewRefreshCandidateResponse;
import com.ds.goroute.mapper.PlaceReviewMapper;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewRepositoryImpl implements PlaceReviewRepository {

    private final PlaceReviewMapper placeReviewMapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Override
    public void insert(PlaceReview review) {
        enforceManagedImages(review);
        placeReviewMapper.insert(review);
    }

    @Override
    public void insertBatch(List<PlaceReview> reviews) {
        if (reviews != null && !reviews.isEmpty()) {
            reviews.forEach(this::enforceManagedImages);
            placeReviewMapper.insertBatch(reviews);
        }
    }

    @Override
    public void update(PlaceReview review) {
        enforceManagedImages(review);
        placeReviewMapper.update(review);
    }
    
    @Override
    public void updateBatch(List<PlaceReview> reviews) {
        if (reviews != null && !reviews.isEmpty()) {
            reviews.forEach(this::enforceManagedImages);
            placeReviewMapper.updateBatch(reviews);
        }
    }

    @Override
    public List<PlaceReview> findByPlaceId(UUID placeId) {
        return placeReviewMapper.findByPlaceId(placeId);
    }

    @Override
    public List<PlaceReview> findAll() {
        return placeReviewMapper.findAll();
    }

    @Override
    public List<PlaceReviewRefreshCandidateResponse> findRefreshCandidates(
            UUID placeId, LocalDateTime cutoff, boolean includeRecent) {
        return placeReviewMapper.findRefreshCandidates(placeId, cutoff, includeRecent);
    }

    @Override
    public Optional<PlaceReview> findByReviewId(String reviewId) {
        return Optional.ofNullable(placeReviewMapper.findByReviewId(reviewId));
    }

    @Override
    public List<PlaceReview> findTopReviewsByPlaceId(UUID placeId, int limit, int offset) {
        return placeReviewMapper.findTopReviewsByPlaceId(placeId, limit, offset);
    }

    @Override
    public List<PlaceReview> findReviewsByPlaceIdAndRating(UUID placeId, int rating, BigDecimal minAuthScore, int limit) {
        return placeReviewMapper.findReviewsByPlaceIdAndRating(placeId, rating, minAuthScore, limit);
    }
    
    @Override
    public List<PlaceReview> findByPlaceIdPaginated(UUID placeId, int limit, int offset) {
        return placeReviewMapper.findByPlaceIdPaginated(placeId, limit, offset);
    }

    @Override
    public BigDecimal getAvgAuthenticityScore(UUID placeId) {
        return placeReviewMapper.getAvgAuthenticityScore(placeId);
    }

    @Override
    public void deleteByPlaceId(UUID placeId) {
        placeReviewMapper.deleteByPlaceId(placeId);
    }
    
    @Override
    public void deleteByIds(List<UUID> ids) {
        if (ids != null && !ids.isEmpty()) {
            placeReviewMapper.deleteByIds(ids);
        }
    }

    private void enforceManagedImages(PlaceReview review) {
        if (review == null) {
            return;
        }
        if (!isManaged(review.getProfilePicture())) {
            review.setProfilePicture(null);
        }
        if (review.getImages() == null || review.getImages().isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(review.getImages());
            if (!root.isArray()) {
                review.setImages("[]");
                return;
            }
            List<String> managed = new java.util.ArrayList<>();
            root.forEach(node -> {
                if (node.isTextual() && isManaged(node.asText()) && !managed.contains(node.asText())) {
                    managed.add(node.asText());
                }
            });
            review.setImages(objectMapper.writeValueAsString(managed));
        } catch (Exception e) {
            log.warn("Discarding malformed or unmanaged images for review {}: {}",
                    review.getReviewId(), e.getMessage(), e);
            review.setImages("[]");
        }
    }

    private boolean isManaged(String url) {
        return storageService.extractObjectKey(url) != null;
    }
}
