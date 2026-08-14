package com.ds.goroute.repository;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.dto.response.PlaceReviewRefreshCandidateResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceReviewRepository {

    void insert(PlaceReview review);

    void insertBatch(List<PlaceReview> reviews);

    void update(PlaceReview review);
    
    void updateBatch(List<PlaceReview> reviews);

    List<PlaceReview> findByPlaceId(UUID placeId);

    List<PlaceReview> findAll();

    List<PlaceReviewRefreshCandidateResponse> findRefreshCandidates(
            UUID placeId, LocalDateTime cutoff, boolean includeRecent);

    Optional<PlaceReview> findByReviewId(String reviewId);

    List<PlaceReview> findTopReviewsByPlaceId(UUID placeId, int limit, int offset);

    List<PlaceReview> findReviewsByPlaceIdAndRating(UUID placeId, int rating, BigDecimal minAuthScore, int limit);
    
    List<PlaceReview> findByPlaceIdPaginated(UUID placeId, int limit, int offset);

    BigDecimal getAvgAuthenticityScore(UUID placeId);

    void deleteByPlaceId(UUID placeId);
    
    void deleteByIds(List<UUID> ids);
}
