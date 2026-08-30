package com.ds.goroute.mapper;

import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.PassportDefinition;
import com.ds.goroute.entity.PassportReward;
import com.ds.goroute.entity.PassportStamp;
import com.ds.goroute.entity.PassportStampRule;
import com.ds.goroute.entity.PassportTag;
import com.ds.goroute.entity.Province;
import com.ds.goroute.entity.UserPassportTag;
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

    /** Curated Location Image anchors and their user's visit state for the Passport map. */
    List<Map<String, Object>> findPassportLocationImageMap(@Param("userId") UUID userId,
                                                             @Param("radiusKm") double radiusKm);

    // --- stamps ----------------------------------------------------------------------

    List<PassportStampRule> findActiveRules();

    PassportStampRule findRule(@Param("code") String code, @Param("version") int version);

    List<PassportStampRule> findStampRules(@Param("includeInactive") boolean includeInactive);

    int insertStampRule(PassportStampRule rule);

    int updateStampRule(PassportStampRule rule);

    int insertStamp(PassportStamp stamp);

    List<PassportStamp> findStampsByUser(@Param("userId") UUID userId);

    // --- want-to-go ------------------------------------------------------------------

    int addWish(@Param("userId") UUID userId, @Param("provinceCode") String provinceCode);

    int removeWish(@Param("userId") UUID userId, @Param("provinceCode") String provinceCode);

    List<String> findWishedProvinceCodes(@Param("userId") UUID userId);

    // --- configured passport tags ---------------------------------------------------

    String findPlaceProvinceCode(@Param("placeId") UUID placeId);

    List<PassportTag> findActiveQualifyingTags(@Param("placeId") UUID placeId,
                                                @Param("provinceCode") String provinceCode,
                                                @Param("latitude") java.math.BigDecimal latitude,
                                                @Param("longitude") java.math.BigDecimal longitude,
                                                @Param("locationRadiusKm") double locationRadiusKm);

    List<PassportTag> findPassportTagsForPlace(@Param("userId") UUID userId,
                                                @Param("placeId") UUID placeId,
                                                @Param("locationRadiusKm") double locationRadiusKm);

    long countUserCheckinsAtPlace(@Param("userId") UUID userId,
                                  @Param("placeId") UUID placeId);

    /** Number of configured Places for the tag. */
    long countTagPlaces(@Param("tagId") UUID tagId);

    /** Number of configured Places where the user met the per-Place threshold. */
    long countQualifiedTagPlaces(@Param("userId") UUID userId, @Param("tag") PassportTag tag);

    /** Distinct catalogued or map locations in the legacy Passport province scope. */
    long countQualifiedProvinceTagLocations(@Param("userId") UUID userId,
                                            @Param("tag") PassportTag tag);

    /** Distinct catalogued/map locations inside a Passport's Location Image scope. */
    long countQualifiedLocationTagLocations(@Param("userId") UUID userId,
                                             @Param("tag") PassportTag tag,
                                             @Param("locationRadiusKm") double locationRadiusKm);

    /** Existing events that meet a newly configured tag; safe to replay after edits. */
    List<Map<String, Object>> findTagAwardCandidates(@Param("tag") PassportTag tag,
                                                      @Param("locationRadiusKm") double locationRadiusKm);

    int insertUserTag(UserPassportTag userTag);

    List<PassportTag> findEarnedTagsByUser(@Param("userId") UUID userId);

    // --- operator catalogue ---------------------------------------------------------

    List<PassportDefinition> findPassportDefinitions(@Param("includeInactive") boolean includeInactive);

    PassportDefinition findPassportDefinition(@Param("id") UUID id);

    int insertPassportDefinition(PassportDefinition definition);

    int updatePassportDefinition(PassportDefinition definition);

    List<UUID> findPassportLocationImageIds(@Param("passportId") UUID passportId);

    long countExistingLocationImages(@Param("ids") List<UUID> ids);

    long countLocationImagesWithCoordinates(@Param("ids") List<UUID> ids);

    int deletePassportLocationImages(@Param("passportId") UUID passportId);

    int insertPassportLocationImage(@Param("passportId") UUID passportId,
                                    @Param("locationImageId") UUID locationImageId);

    List<String> findPassportProvinceCodes(@Param("passportId") UUID passportId);

    int deletePassportProvinces(@Param("passportId") UUID passportId);

    int insertPassportProvince(@Param("passportId") UUID passportId,
                               @Param("provinceCode") String provinceCode);

    List<PassportTag> findPassportTags(@Param("includeInactive") boolean includeInactive);

    PassportTag findPassportTag(@Param("id") UUID id);

    int insertPassportTag(PassportTag tag);

    int updatePassportTag(PassportTag tag);

    List<UUID> findTagPlaceIds(@Param("tagId") UUID tagId);

    int deleteTagPlaces(@Param("tagId") UUID tagId);

    int insertTagPlace(@Param("tagId") UUID tagId, @Param("placeId") UUID placeId);

    long countExistingPlaces(@Param("ids") List<UUID> ids);

    List<PassportReward> findPassportRewards(@Param("includeInactive") boolean includeInactive);

    PassportReward findPassportReward(@Param("id") UUID id);

    int insertPassportReward(PassportReward reward);

    int updatePassportReward(PassportReward reward);
}
