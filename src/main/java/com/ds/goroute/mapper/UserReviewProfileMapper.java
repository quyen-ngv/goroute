package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserReviewProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserReviewProfileMapper {
    
    void insert(UserReviewProfile profile);
    
    void update(UserReviewProfile profile);
    
    UserReviewProfile findByUserId(@Param("userId") UUID userId);
    
    void incrementReviewCount(@Param("userId") UUID userId);
    
    void decrementReviewCount(@Param("userId") UUID userId);
}
