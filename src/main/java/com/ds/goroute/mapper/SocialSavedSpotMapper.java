package com.ds.goroute.mapper;

import com.ds.goroute.entity.SocialSavedSpot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SocialSavedSpotMapper {
    void upsertAll(@Param("spots") List<SocialSavedSpot> spots);

    List<SocialSavedSpot> findByUserId(@Param("userId") UUID userId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    int deleteOwned(@Param("userId") UUID userId, @Param("id") UUID id);
}
