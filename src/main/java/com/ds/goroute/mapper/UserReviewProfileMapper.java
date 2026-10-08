package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserReviewProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserReviewProfileMapper {
    
    void insert(UserReviewProfile profile);
    
    void update(UserReviewProfile profile);
    
    UserReviewProfile findByUserId(@Param("userId") UUID userId);

    List<UserReviewProfile> findByUserIds(@Param("userIds") Collection<UUID> userIds);
    
    void incrementReviewCount(@Param("userId") UUID userId);
    
    void decrementReviewCount(@Param("userId") UUID userId);
}
