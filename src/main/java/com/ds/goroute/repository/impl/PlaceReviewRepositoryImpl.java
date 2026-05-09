package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.mapper.PlaceReviewMapper;
import com.ds.goroute.repository.PlaceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public List<PlaceReview> findByPlaceId(UUID placeId) {
        return placeReviewMapper.findByPlaceId(placeId);
    }
    
    @Override
    public void deleteByPlaceId(UUID placeId) {
        placeReviewMapper.deleteByPlaceId(placeId);
    }
}
