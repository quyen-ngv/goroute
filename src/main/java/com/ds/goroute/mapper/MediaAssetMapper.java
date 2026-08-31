package com.ds.goroute.mapper;

import com.ds.goroute.entity.MediaAsset;
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
    List<MediaAsset> selectByEntity(@Param("entityType") String entityType,
                                    @Param("entityId") UUID entityId);
    List<MediaAsset> selectByEntityIds(@Param("entityType") String entityType,
                                       @Param("entityIds") List<UUID> entityIds);
    int updateDetails(MediaAsset mediaAsset);
    int softDelete(@Param("id") UUID id);
    int softDeleteByEntity(@Param("entityType") String entityType,
                           @Param("entityId") UUID entityId);
}
