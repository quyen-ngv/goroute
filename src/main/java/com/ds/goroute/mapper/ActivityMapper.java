package com.ds.goroute.mapper;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.entity.UnlinkedActivityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityMapper {
    int insert(Activity activity);
    
    Activity selectById(@Param("id") UUID id);
    
    List<Activity> selectAll();
    
    List<Activity> selectByTripId(@Param("tripId") UUID tripId);
    
    int countByTripId(@Param("tripId") UUID tripId);

    /** Activity count per trip; trips without activities have no row. */
    List<TripCount> countByTripIds(@Param("tripIds") List<UUID> tripIds);

    /** Id, trip id and name only: enough to label photos with the stop they belong to. */
    List<Activity> selectNamesByIds(@Param("ids") List<UUID> ids);

    UUID selectTripIdById(@Param("id") UUID id);

    List<Activity> selectByTripIdAndDay(@Param("tripId") UUID tripId, @Param("dayNumber") Integer dayNumber);
    
    List<Activity> selectByTripIdAndDayNumber(@Param("tripId") UUID tripId, @Param("dayNumber") int dayNumber);
    
    int updateById(Activity activity);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByTripId(@Param("tripId") UUID tripId);
    
    int updateSortOrder(@Param("id") UUID id, @Param("sortOrder") Integer sortOrder);

    List<UnlinkedActivityRow> selectUnlinkedForAdmin(
            @Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);

    long countUnlinkedForAdmin(@Param("search") String search);

    int assignPlace(@Param("id") UUID id, @Param("placeId") String placeId, @Param("placeRefId") UUID placeRefId,
                    @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng);
}
