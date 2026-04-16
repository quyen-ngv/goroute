package com.ds.goroute.mapper;

import com.ds.goroute.entity.ActivityComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityCommentMapper {
    
    ActivityComment selectById(@Param("id") UUID id);
    
    List<ActivityComment> selectByActivityId(@Param("activityId") UUID activityId);
    
    int insert(ActivityComment comment);
    
    int updateById(ActivityComment comment);
    
    int softDelete(@Param("id") UUID id);
    
    int deleteById(@Param("id") UUID id);
    
    int countByActivityId(@Param("activityId") UUID activityId);
}
