package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserGuideGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface UserGuideMapper {

    UserGuideGrant find(@Param("userId") UUID userId);

    /** Ids among the given ones that are guides right now, for badging a list in one query. */
    List<UUID> findActiveIds(@Param("userIds") Collection<UUID> userIds);

    int upsertActive(UserGuideGrant grant);

    int revoke(@Param("userId") UUID userId,
               @Param("revokedBy") UUID revokedBy,
               @Param("reason") String reason);

    /** The guide directory for the console, joined to the account it belongs to. */
    List<Map<String, Object>> findAll(@Param("status") String status,
                                      @Param("search") String search,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    long countAll(@Param("status") String status, @Param("search") String search);

    /** Tourist areas this guide covers. */
    List<UUID> findLocationImageIds(@Param("userId") UUID userId);

    int deleteLocationImages(@Param("userId") UUID userId);

    int insertLocationImage(@Param("userId") UUID userId,
                            @Param("locationImageId") UUID locationImageId);
}
