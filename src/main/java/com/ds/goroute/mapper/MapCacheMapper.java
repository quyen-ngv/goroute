package com.ds.goroute.mapper;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.entity.RouteLegCacheEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** The map caches: places, whole answers, and road legs (epic 06). */
@Mapper
public interface MapCacheMapper {

    /** Inserts or refreshes a place; {@code picked_count} is never touched here. */
    int upsertPlace(@Param("entry") MapPlaceCacheEntry entry);

    MapPlaceCacheEntry findPlace(@Param("provider") String provider,
                                 @Param("providerPlaceId") String providerPlaceId);

    int incrementPicked(@Param("provider") String provider,
                        @Param("providerPlaceId") String providerPlaceId);

    /**
     * Places users already chose whose normalised name contains, or closely resembles, the
     * query. With a position, only those within {@code radiusKm}; nearest-similar first.
     */
    List<MapPlaceCacheEntry> searchPicked(@Param("query") String normalizedQuery,
                                          @Param("latitude") Double latitude,
                                          @Param("longitude") Double longitude,
                                          @Param("radiusKm") double radiusKm,
                                          @Param("limit") int limit);

    String findResponse(@Param("cacheKey") String cacheKey, @Param("now") LocalDateTime now);

    int upsertResponse(@Param("cacheKey") String cacheKey,
                       @Param("operation") String operation,
                       @Param("provider") String provider,
                       @Param("body") String body,
                       @Param("expiresAt") LocalDateTime expiresAt);

    int deleteExpiredResponses(@Param("now") LocalDateTime now);

    List<RouteLegCacheEntry> findLegs(@Param("legKeys") Collection<String> legKeys);

    int upsertLeg(@Param("entry") RouteLegCacheEntry entry);

    int touchLegs(@Param("legKeys") Collection<String> legKeys);

    int deleteLegsUnusedSince(@Param("cutoff") LocalDateTime cutoff);
}
