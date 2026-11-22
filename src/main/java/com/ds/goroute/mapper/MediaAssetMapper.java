package com.ds.goroute.mapper;

import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.TripCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MediaAssetMapper {
    int insert(MediaAsset mediaAsset);
    MediaAsset selectById(@Param("id") UUID id);
    List<MediaAsset> selectByTripId(@Param("tripId") UUID tripId);
    List<MediaAsset> selectByActivityId(@Param("activityId") UUID activityId);
    int countByTripId(@Param("tripId") UUID tripId);
    List<MediaAsset> selectTripMemoriesByTripId(@Param("tripId") UUID tripId);
    List<MediaAsset> selectTripMemoriesByActivityId(@Param("activityId") UUID activityId);
    int countTripMemoriesByTripId(@Param("tripId") UUID tripId);
    List<MediaAsset> selectByEntity(@Param("entityType") String entityType,
                                    @Param("entityId") UUID entityId);
    List<MediaAsset> selectByEntityType(@Param("entityType") String entityType);
    List<MediaAsset> selectByEntityIds(@Param("entityType") String entityType,
                                       @Param("entityIds") List<UUID> entityIds);
    int updateDetails(MediaAsset mediaAsset);
    int updatePosition(MediaAsset mediaAsset);
    int updateUrl(@Param("id") UUID id, @Param("url") String url);
    int softDelete(@Param("id") UUID id);
    int softDeleteByEntity(@Param("entityType") String entityType,
                           @Param("entityId") UUID entityId);
    /** The first {@code perTrip} memories of each trip, in gallery order (trip card previews). */
    List<MediaAsset> selectTripMemoryPreviewsByTripIds(@Param("tripIds") List<UUID> tripIds,
                                                       @Param("perTrip") int perTrip);
    /** Memory count per trip; trips without memories have no row. */
    List<TripCount> countTripMemoriesByTripIds(@Param("tripIds") List<UUID> tripIds);
}
