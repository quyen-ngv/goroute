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

    Place findByCid(@Param("cid") String cid);

    List<String> findExistingPlaceIds(@Param("placeIds") List<String> placeIds);

    List<String> findExistingCids(@Param("cids") List<String> cids);

    Place findNearCoordinates(@Param("latitude") BigDecimal latitude,
                              @Param("longitude") BigDecimal longitude,
                              @Param("maxDistanceMeters") BigDecimal maxDistanceMeters);

    List<Place> findAll();

    long countAll();

    List<Place> findPage(@Param("limit") int limit, @Param("offset") int offset);

    List<Place> findByIds(@Param("ids") List<UUID> ids);

    List<Place> findForAiByDestination(@Param("citySlugJson") String citySlugJson,
                                       @Param("latitude") BigDecimal latitude,
                                       @Param("longitude") BigDecimal longitude,
                                       @Param("placeGroup") String placeGroup,
                                       @Param("minRating") BigDecimal minRating,
                                       @Param("limit") int limit);

    List<Place> findActiveForAiWithinRadius(@Param("latitude") BigDecimal latitude,
                                            @Param("longitude") BigDecimal longitude,
                                            @Param("radiusKm") BigDecimal radiusKm,
                                            @Param("placeGroups") List<String> placeGroups,
                                            @Param("limit") int limit);

    void delete(@Param("id") UUID id);
}
