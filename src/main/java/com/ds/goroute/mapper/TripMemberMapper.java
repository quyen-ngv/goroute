package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripMemberMapper {
    int insert(TripMember member);
    
    TripMember selectById(@Param("id") UUID id);
    
    List<TripMember> selectByTripId(@Param("tripId") UUID tripId);
    
    List<TripMember> selectByUserId(@Param("userId") UUID userId);
    
    TripMember selectByTripIdAndUserId(@Param("tripId") UUID tripId, @Param("userId") UUID userId);
    
    int updateById(TripMember member);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByTripIdAndUserId(@Param("tripId") UUID tripId, @Param("userId") UUID userId);
    
    List<TripMember> selectPendingByUserId(@Param("userId") UUID userId);
    
    List<TripMember> selectGuestsByEmail(@Param("email") String email);
}
