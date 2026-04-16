package com.ds.goroute.mapper;

import com.ds.goroute.entity.Trip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripMapper {
    int insert(Trip trip);
    
    Trip selectById(@Param("id") UUID id);
    
    List<Trip> selectByOwnerId(@Param("ownerId") UUID ownerId);
    
    List<Trip> selectByUserId(@Param("userId") UUID userId);
    
    int updateById(Trip trip);
    
    int deleteById(@Param("id") UUID id);
    
    Trip selectByShareCode(@Param("shareCode") String shareCode);
}
