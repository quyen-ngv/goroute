package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceSocialVideo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceSocialVideoMapper {
    void upsert(PlaceSocialVideo video);

    List<PlaceSocialVideo> findByPlaceId(@Param("placeId") UUID placeId);
}
