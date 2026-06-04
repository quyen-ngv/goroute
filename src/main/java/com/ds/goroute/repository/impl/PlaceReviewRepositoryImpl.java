package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.mapper.PlaceReviewMapper;
import com.ds.goroute.repository.PlaceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceReviewRepositoryImpl implements PlaceReviewRepository {

    private final PlaceReviewMapper placeReviewMapper;

    @Override
    public void insert(PlaceReview review) {
        placeReviewMapper.insert(review);
    }

    @Override
    public void insertBatch(List<PlaceReview> reviews) {
        if (reviews != null && !reviews.isEmpty()) {
            placeReviewMapper.insertBatch(reviews);
        }
    }

    @Override
    public void update(PlaceReview review) {
        placeReviewMapper.update(review);
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
    public BigDecimal getAvgAuthenticityScore(UUID placeId) {
        return placeReviewMapper.getAvgAuthenticityScore(placeId);
    }

    @Override
    public void deleteByPlaceId(UUID placeId) {
        placeReviewMapper.deleteByPlaceId(placeId);
    }
}
