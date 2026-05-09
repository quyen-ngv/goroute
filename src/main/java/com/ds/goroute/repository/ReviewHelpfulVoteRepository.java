package com.ds.goroute.repository;

import com.ds.goroute.entity.ReviewHelpfulVote;
import java.util.UUID;

public interface ReviewHelpfulVoteRepository {
    void save(ReviewHelpfulVote vote);
    void update(ReviewHelpfulVote vote);
    void delete(UUID reviewId, UUID userId);
    boolean exists(UUID reviewId, UUID userId);
    int countByReviewId(UUID reviewId);
    ReviewHelpfulVote findByReviewIdAndUserId(UUID reviewId, UUID userId);
    int countByReviewIdAndIsHelpful(UUID reviewId, boolean isHelpful);
}
