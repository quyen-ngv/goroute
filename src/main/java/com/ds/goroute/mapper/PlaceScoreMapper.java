package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface PlaceScoreMapper {
    
    void insert(PlaceScore score);
    
    void update(PlaceScore score);
    
    PlaceScore findByPlaceId(@Param("placeId") UUID placeId);
}
