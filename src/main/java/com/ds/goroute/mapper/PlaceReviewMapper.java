package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceReviewMapper {

    void insert(PlaceReview review);

    void insertBatch(@Param("reviews") List<PlaceReview> reviews);

    void update(PlaceReview review);

    List<PlaceReview> findByPlaceId(@Param("placeId") UUID placeId);

    List<PlaceReview> findAll();

    PlaceReview findByReviewId(@Param("reviewId") String reviewId);

    List<PlaceReview> findTopReviewsByPlaceId(
            @Param("placeId") UUID placeId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<PlaceReview> findReviewsByPlaceIdAndRating(
            @Param("placeId") UUID placeId,
            @Param("rating") int rating,
            @Param("minAuthScore") BigDecimal minAuthScore,
            @Param("limit") int limit
    );

    BigDecimal getAvgAuthenticityScore(@Param("placeId") UUID placeId);

    void deleteByPlaceId(@Param("placeId") UUID placeId);
}
