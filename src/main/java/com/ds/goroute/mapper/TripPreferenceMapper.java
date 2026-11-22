package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripPreferenceMapper {
    void upsert(TripPreference preference);

    void deleteTags(@Param("tripId") UUID tripId);

    void insertTags(@Param("tripId") UUID tripId, @Param("kind") String kind, @Param("values") List<String> values);

    TripPreference findByTripId(@Param("tripId") UUID tripId);

    List<String> findTags(@Param("tripId") UUID tripId, @Param("kind") String kind);
}
