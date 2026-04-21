package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripNoteMapper {
    
    TripNote selectById(@Param("id") UUID id);
    
    List<TripNote> selectByTripId(@Param("tripId") UUID tripId);
    
    List<TripNote> selectByActivityId(@Param("activityId") UUID activityId);
    
    int insert(TripNote note);
    
    int updateById(TripNote note);
    
    int softDelete(@Param("id") UUID id);
    
    int deleteById(@Param("id") UUID id);
    
    int countByTripId(@Param("tripId") UUID tripId);
}
