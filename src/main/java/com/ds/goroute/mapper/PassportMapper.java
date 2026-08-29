package com.ds.goroute.mapper;

import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.PassportStamp;
import com.ds.goroute.entity.PassportStampRule;
import com.ds.goroute.entity.Province;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface PassportMapper {

    // --- provinces -------------------------------------------------------------------

    List<Province> findProvinces(@Param("datasetVersion") String datasetVersion);

    Province findProvinceByCode(@Param("code") String code);

    long countProvinces();

    /** Share of catalogued places that have a province, the progress measure for PAS-01. */
    Map<String, Object> provinceCoverage();

    List<Map<String, Object>> findAmbiguousPlaces(@Param("limit") int limit, @Param("offset") int offset);

    int assignPlaceProvince(@Param("placeId") UUID placeId,
                            @Param("provinceCode") String provinceCode,
                            @Param("status") String status,
                            @Param("confidence") Double confidence);

    List<Map<String, Object>> findPlacesWithoutProvince(@Param("limit") int limit);

    // --- events ----------------------------------------------------------------------

    /** No-op when this source row already produced an event. */
    int insertEvent(PassportEvent event);

    List<PassportEvent> findEventsByUser(@Param("userId") UUID userId,
                                         @Param("includeHidden") boolean includeHidden,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    long countEventsByUser(@Param("userId") UUID userId);

    List<PassportEvent> findEventsByProvince(@Param("userId") UUID userId,
                                             @Param("provinceCode") String provinceCode,
                                             @Param("limit") int limit);

    int setEventHidden(@Param("id") UUID id, @Param("userId") UUID userId, @Param("hidden") boolean hidden);

    /** Activity check-ins that predate the passport and still need to be brought in. */
    List<Map<String, Object>> findLegacyActivityCheckins(@Param("limit") int limit);

    // --- counters that the stamp rules read -------------------------------------------

    Map<String, Object> summarizeUser(@Param("userId") UUID userId);

    List<String> findVisitedProvinceCodes(@Param("userId") UUID userId);

    /** Visits per province, for the passport map. One query, not one per province. */
    List<Map<String, Object>> countEventsByProvince(@Param("userId") UUID userId);

    // --- stamps ----------------------------------------------------------------------

    List<PassportStampRule> findActiveRules();

    PassportStampRule findRule(@Param("code") String code, @Param("version") int version);

    int insertStamp(PassportStamp stamp);

    List<PassportStamp> findStampsByUser(@Param("userId") UUID userId);

    // --- want-to-go ------------------------------------------------------------------

    int addWish(@Param("userId") UUID userId, @Param("provinceCode") String provinceCode);

    int removeWish(@Param("userId") UUID userId, @Param("provinceCode") String provinceCode);

    List<String> findWishedProvinceCodes(@Param("userId") UUID userId);
}
