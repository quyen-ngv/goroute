package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mapper
public interface AdminMapper {
    boolean hasPermission(@Param("userId") UUID userId,
                          @Param("resource") String resource,
                          @Param("action") String action);

    boolean ownsResource(@Param("ownerId") UUID ownerId,
                         @Param("resource") String resource,
                         @Param("resourceId") UUID resourceId);

    List<Map<String, Object>> findUsers(@Param("search") String search,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    List<Map<String, Object>> findRoles();

    Map<String, Object> findUserDetail(@Param("userId") UUID userId);

    List<Map<String, Object>> findUserTrips(@Param("userId") UUID userId);

    List<Map<String, Object>> findUserContributions(@Param("userId") UUID userId);

    List<Map<String, Object>> findUserMedia(@Param("userId") UUID userId);

    int deleteUserRoles(@Param("userId") UUID userId);

    int insertUserRoles(@Param("userId") UUID userId, @Param("roleCodes") Set<String> roleCodes);

    List<Map<String, Object>> findMedia(@Param("search") String search);

    int insertMedia(@Param("id") UUID id,
                    @Param("url") String url,
                    @Param("caption") String caption,
                    @Param("uploadedBy") UUID uploadedBy);

    int softDeleteMedia(@Param("id") UUID id);

    List<Map<String, Object>> findPlans(@Param("search") String search,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    Map<String, Object> findPlanDetail(@Param("planId") UUID planId);

    List<Map<String, Object>> findPlanActivities(@Param("planId") UUID planId);

    List<Map<String, Object>> findPlanMembers(@Param("planId") UUID planId);
}
