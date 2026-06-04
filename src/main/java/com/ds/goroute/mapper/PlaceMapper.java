package com.ds.goroute.mapper;

import com.ds.goroute.entity.Place;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceMapper {

    void insert(Place place);

    void update(Place place);

    Place findById(@Param("id") UUID id);

    Place findByPlaceId(@Param("placeId") String placeId);

    List<Place> findAll();

    List<Place> findNearby(@Param("keyword") String keyword,
                          @Param("latitude") BigDecimal latitude,
                          @Param("longitude") BigDecimal longitude,
                          @Param("radius") BigDecimal radius,
                          @Param("category") String category,
                          @Param("placeGroup") String placeGroup,
                          @Param("minRating") BigDecimal minRating,
                          @Param("limit") int limit,
                          @Param("offset") int offset);

    List<Place> findNearbyExtended(@Param("keyword") String keyword,
                                   @Param("latitude") BigDecimal latitude,
                                   @Param("longitude") BigDecimal longitude,
                                   @Param("radius") BigDecimal radius,
                                   @Param("category") String category,
                                   @Param("placeGroup") String placeGroup,
                                   @Param("minRating") BigDecimal minRating,
                                   @Param("citySlugJson") String citySlugJson,
                                   @Param("foodIds") List<UUID> foodIds,
                                   @Param("excludeLinkedFoodPlaces") Boolean excludeLinkedFoodPlaces,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    void delete(@Param("id") UUID id);
}
