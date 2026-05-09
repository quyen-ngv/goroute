package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ReviewHelpfulVote;
import com.ds.goroute.mapper.ReviewHelpfulVoteMapper;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewHelpfulVoteRepositoryImpl implements ReviewHelpfulVoteRepository {
    private final ReviewHelpfulVoteMapper mapper;
    
    @Override
    public void save(ReviewHelpfulVote vote) {
        mapper.insert(vote);
    }
    
    @Override
    public void update(ReviewHelpfulVote vote) {
        mapper.update(vote);
    }
    
    @Override
    public void delete(UUID reviewId, UUID userId) {
        mapper.delete(reviewId, userId);
    }
    
    @Override
    public boolean exists(UUID reviewId, UUID userId) {
        return mapper.exists(reviewId, userId);
    }
    
    @Override
    public int countByReviewId(UUID reviewId) {
        return mapper.countByReviewId(reviewId);
    }
    
    @Override
    public ReviewHelpfulVote findByReviewIdAndUserId(UUID reviewId, UUID userId) {
        return mapper.findByReviewIdAndUserId(reviewId, userId);
    }
    
    @Override
    public int countByReviewIdAndIsHelpful(UUID reviewId, boolean isHelpful) {
        return mapper.countByReviewIdAndIsHelpful(reviewId, isHelpful);
    }
}
