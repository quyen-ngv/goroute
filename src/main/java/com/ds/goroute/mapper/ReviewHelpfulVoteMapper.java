package com.ds.goroute.mapper;

import com.ds.goroute.entity.ReviewHelpfulVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface ReviewHelpfulVoteMapper {
    
    void insert(ReviewHelpfulVote vote);
    
    void update(ReviewHelpfulVote vote);
    
    void delete(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);
    
    boolean exists(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);
    
    int countByReviewId(@Param("reviewId") UUID reviewId);
    
    ReviewHelpfulVote findByReviewIdAndUserId(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);
    
    int countByReviewIdAndIsHelpful(@Param("reviewId") UUID reviewId, @Param("isHelpful") boolean isHelpful);
}
