package com.ds.goroute.mapper;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripRankRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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
    
    /** One page of the public trip search ranking: ids and owners, best first. */
    List<TripRankRow> searchPublicTripRanking(
        @Param("criteria") PublicTripSearchCriteria criteria,
        @Param("excludeUserId") UUID excludeUserId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    /** The public, live, not taken-down trips among {@code ids}, in no particular order. */
    List<Trip> selectPublicByIds(@Param("ids") List<UUID> ids);

    int refreshActivityCount(@Param("tripId") UUID tripId);

    int refreshMemoryCount(@Param("tripId") UUID tripId);

    int reconcileContentCounts();

    /**
     * Public trips owned by the people {@code viewerId} follows, most recently shared first;
     * with a focus point, those in its province first, then by 100 km ring (see the XML).
     */
    List<Trip> findFollowingTrips(@Param("viewerId") UUID viewerId,
                                  @Param("focusLat") Double focusLat,
                                  @Param("focusLng") Double focusLng,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /**
     * Ids of the public, live, not taken-down trips with a non-rejected activity on the
     * catalogue place {@code placeId}, most recently shared first.
     */
    List<UUID> selectPublicTripIdsByPlace(@Param("placeId") UUID placeId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    int incrementViewCount(@Param("id") UUID id);
    
    int incrementCopyCount(@Param("id") UUID id);
    
    Trip selectMostRecentByUserId(@Param("userId") UUID userId);

    List<Trip> selectPublicTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int countPublicTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int countTripsByOwnerId(@Param("ownerId") UUID ownerId);

    int sumCopyCountByOwnerId(@Param("ownerId") UUID ownerId);

    int sumHelpfulVotesByOwnerId(@Param("ownerId") UUID ownerId);

    int updateVoteCounts(Trip trip);

    List<Trip> selectNotificationCandidates(@Param("fromDate") LocalDate fromDate,
                                            @Param("toDate") LocalDate toDate);
}
