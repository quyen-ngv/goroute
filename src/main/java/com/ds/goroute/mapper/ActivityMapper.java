package com.ds.goroute.mapper;

import com.ds.goroute.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityMapper {
    int insert(Activity activity);
    
    Activity selectById(@Param("id") UUID id);
    
    List<Activity> selectByTripId(@Param("tripId") UUID tripId);
    
    List<Activity> selectByTripIdAndDay(@Param("tripId") UUID tripId, @Param("dayNumber") Integer dayNumber);
    
    List<Activity> selectByTripIdAndDayNumber(@Param("tripId") UUID tripId, @Param("dayNumber") int dayNumber);
    
    int updateById(Activity activity);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByTripId(@Param("tripId") UUID tripId);
    
    int updateSortOrder(@Param("id") UUID id, @Param("sortOrder") Integer sortOrder);
}
