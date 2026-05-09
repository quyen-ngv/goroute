package com.ds.goroute.repository;

import com.ds.goroute.entity.PlaceReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceReviewRepository {
    
    void insert(PlaceReview review);
    
    void insertBatch(List<PlaceReview> reviews);
    
    List<PlaceReview> findByPlaceId(UUID placeId);
    
    void deleteByPlaceId(UUID placeId);
}
