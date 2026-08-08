package com.ds.goroute.mapper;

import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SocialLocationRestrictionMapper {
    SocialLocationUserRestriction findByUserId(@Param("userId") UUID userId);
    void upsert(SocialLocationUserRestriction restriction);
    void insertEvent(SocialLocationSubmissionEvent event);
    List<SocialLocationUserRestriction> findAdmin(@Param("status") String status,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);
    List<SocialLocationSubmissionEvent> findEventsByUserId(@Param("userId") UUID userId,
                                                            @Param("limit") int limit);
    void reset(@Param("userId") UUID userId);
}
