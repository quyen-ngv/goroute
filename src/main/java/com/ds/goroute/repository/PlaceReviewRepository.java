package com.ds.goroute.repository;

import com.ds.goroute.entity.PlaceReview;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceReviewRepository {

    void insert(PlaceReview review);

    void insertBatch(List<PlaceReview> reviews);

    void update(PlaceReview review);

    List<PlaceReview> findByPlaceId(UUID placeId);

    List<PlaceReview> findAll();

    Optional<PlaceReview> findByReviewId(String reviewId);

    List<PlaceReview> findTopReviewsByPlaceId(UUID placeId, int limit, int offset);

    List<PlaceReview> findReviewsByPlaceIdAndRating(UUID placeId, int rating, BigDecimal minAuthScore, int limit);

    BigDecimal getAvgAuthenticityScore(UUID placeId);

    void deleteByPlaceId(UUID placeId);
}
