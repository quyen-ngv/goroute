package com.ds.goroute.mapper;

import com.ds.goroute.entity.Trip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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
    
    List<Trip> searchPublicTrips(
        @Param("latitude") BigDecimal latitude,
        @Param("longitude") BigDecimal longitude,
        @Param("radiusKm") BigDecimal radiusKm,
        @Param("destination") String destination,
        @Param("keyword") String keyword,
        @Param("allPublic") boolean allPublic,
        @Param("randomSeed") String randomSeed,
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("excludeUserId") UUID excludeUserId
    );
    
    int incrementViewCount(@Param("id") UUID id);
    
    int incrementCopyCount(@Param("id") UUID id);
    
    Trip selectMostRecentByUserId(@Param("userId") UUID userId);

    List<Trip> selectPublicTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int countPublicTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int countTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int sumCopyCountByOwnerId(@Param("ownerId") UUID ownerId);

    int sumHelpfulVotesByOwnerId(@Param("ownerId") UUID ownerId);

    int updateVoteCounts(Trip trip);
}
