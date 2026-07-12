package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripDestination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripDestinationMapper {
    int insert(TripDestination destination);

    List<TripDestination> selectByTripId(@Param("tripId") UUID tripId);

    TripDestination selectById(@Param("id") UUID id);

    int deleteByTripId(@Param("tripId") UUID tripId);
}
