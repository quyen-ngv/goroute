package com.ds.goroute.mapper;

import com.ds.goroute.entity.Checkin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CheckinMapper {
    int insert(Checkin checkin);
    
    Checkin selectById(@Param("id") UUID id);
    
    List<Checkin> selectByActivityId(@Param("activityId") UUID activityId);
    
    List<Checkin> selectByTripId(@Param("tripId") UUID tripId);
    
    List<Checkin> selectByUserId(@Param("userId") UUID userId);
    
    Checkin selectByActivityIdAndUserId(@Param("activityId") UUID activityId, @Param("userId") UUID userId);
    
    int updateById(Checkin checkin);
    
    int deleteById(@Param("id") UUID id);
}
