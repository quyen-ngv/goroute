package com.ds.goroute.mapper;

import com.ds.goroute.entity.ReviewFlag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewFlagMapper {
    
    void insert(ReviewFlag flag);
    
    List<ReviewFlag> findByReviewId(@Param("reviewId") UUID reviewId);
    
    int countByReviewId(@Param("reviewId") UUID reviewId);
}
