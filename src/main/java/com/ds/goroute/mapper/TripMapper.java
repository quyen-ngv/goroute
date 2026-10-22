package com.ds.goroute.mapper;

import com.ds.goroute.dto.FeedPlaceKind;
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
        @Param("viewerId") UUID viewerId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    /** The public, live, not taken-down trips among {@code ids}, in no particular order. */
    List<Trip> selectPublicByIds(@Param("ids") List<UUID> ids);

    int refreshActivityCount(@Param("tripId") UUID tripId);

    int claimFollowersNotification(@Param("tripId") UUID tripId);

    int refreshMemoryCount(@Param("tripId") UUID tripId);

    int reconcileContentCounts();

    /**
     * Public trips owned by the people {@code viewerId} follows, most recently shared first;
     * with a focus point, those in its province first, then by 100 km ring (see the XML).
     */
    List<Trip> findFollowingTrips(@Param("viewerId") UUID viewerId,
                                  @Param("focusLat") Double focusLat,
                                  @Param("focusLng") Double focusLng,
                                  @Param("kind") FeedPlaceKind kind,
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

    /** Trips with a destination point but no resolved zone yet, oldest change first. */
    List<Trip> selectWithoutTimezone(@Param("limit") int limit);

    /** Sets the zone only while the destination is still the one it was resolved for. */
    int updateTimezone(@Param("id") UUID id,
                       @Param("timezone") String timezone,
                       @Param("destinationLat") java.math.BigDecimal destinationLat,
                       @Param("destinationLng") java.math.BigDecimal destinationLng);

    /** Feed search index: the next {@code limit} findable trips after {@code afterId}, by id. */
    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexPage(@Param("afterId") UUID afterId,
                                                                          @Param("limit") int limit);

    /** Feed search index: trips changed after the cursor, findable or not, oldest change first. */
    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexChanges(
            @Param("afterUpdatedAt") java.time.LocalDateTime afterUpdatedAt,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    java.time.LocalDateTime selectSearchIndexMaxUpdatedAt();
}
