package com.ds.goroute.repository;

import com.ds.goroute.entity.ReviewFlag;
import java.util.List;
import java.util.UUID;

public interface ReviewFlagRepository {
    void save(ReviewFlag flag);
    List<ReviewFlag> findByReviewId(UUID reviewId);
    int countByReviewId(UUID reviewId);
}
