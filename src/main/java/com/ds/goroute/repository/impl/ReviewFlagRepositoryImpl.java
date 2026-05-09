package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ReviewFlag;
import com.ds.goroute.mapper.ReviewFlagMapper;
import com.ds.goroute.repository.ReviewFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewFlagRepositoryImpl implements ReviewFlagRepository {
    private final ReviewFlagMapper mapper;
    
    @Override
    public void save(ReviewFlag flag) {
        mapper.insert(flag);
    }
    
    @Override
    public List<ReviewFlag> findByReviewId(UUID reviewId) {
        return mapper.findByReviewId(reviewId);
    }
    
    @Override
    public int countByReviewId(UUID reviewId) {
        return mapper.countByReviewId(reviewId);
    }
}
