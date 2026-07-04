package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface TripBookMapper {
    int insert(TripBook tripBook);

    TripBook selectById(@Param("id") UUID id);

    TripBook selectByTripId(@Param("tripId") UUID tripId);

    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
