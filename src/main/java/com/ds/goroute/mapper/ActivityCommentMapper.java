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

    /**
     * Comment counts for every activity of one trip, in one query.
     *
     * <p>Per-activity counting would be one query per card on the itinerary screen; this
     * is what lets the entry point carry a count without that.
     */
    List<java.util.Map<String, Object>> countByTripId(@Param("tripId") UUID tripId);
}
