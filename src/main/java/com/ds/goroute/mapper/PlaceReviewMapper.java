package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceReviewMapper {
    
    void insert(PlaceReview review);
    
    void insertBatch(@Param("reviews") List<PlaceReview> reviews);
    
    List<PlaceReview> findByPlaceId(@Param("placeId") UUID placeId);
    
    void deleteByPlaceId(@Param("placeId") UUID placeId);
}
