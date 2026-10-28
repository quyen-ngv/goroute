package com.ds.goroute.mapper;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTrendingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceMapper {

    void insert(Place place);

    void update(Place place);

    void updateReviewRefreshMetadata(Place place);

    Place findById(@Param("id") UUID id);

    Place findByPlaceId(@Param("placeId") String placeId);

    Place findByCid(@Param("cid") String cid);

    List<String> findExistingPlaceIds(@Param("placeIds") List<String> placeIds);

    List<String> findExistingCids(@Param("cids") List<String> cids);

    Place findNearCoordinates(@Param("latitude") BigDecimal latitude,
                              @Param("longitude") BigDecimal longitude,
                              @Param("maxDistanceMeters") BigDecimal maxDistanceMeters);

    /**
     * The nearest {@code limit} places within the radius, carrying only what a duplicate
     * check compares (id, Google ids, title, address, coordinates, distance in metres).
     */
    List<Place> findNearbyIdentityCandidates(@Param("latitude") BigDecimal latitude,
                                             @Param("longitude") BigDecimal longitude,
                                             @Param("maxDistanceMeters") BigDecimal maxDistanceMeters,
                                             @Param("limit") int limit);

    boolean existsById(@Param("id") UUID id);

    /** Ward and province of one place, nothing else. */
    Place findGeoCodes(@Param("id") UUID id);

    /** What a place card shows, for the given ids; heavy JSON columns are left out. */
    List<Place> findListSummaryByIds(@Param("ids") List<UUID> ids,
                                     @Param("publicOnly") boolean publicOnly);

    /**
     * Public places within {@code radiusMeters} that trips or check-ins used in the last
     * {@code days} days, most used first (the {@code PlaceProminence} score over the window).
     */
    List<PlaceTrendingRow> findTrendingUsage(@Param("latitude") BigDecimal latitude,
                                             @Param("longitude") BigDecimal longitude,
                                             @Param("radiusMeters") BigDecimal radiusMeters,
                                             @Param("placeGroups") List<String> placeGroups,
                                             @Param("days") int days,
                                             @Param("limit") int limit);

    /** What a map pin shows, for the given ids: its address and photos too. Public places only. */
    List<Place> findMapPinsByIds(@Param("ids") List<UUID> ids);

    /**
     * Recounts trip_use_count and checkin_count (V225) of {@code placeIds}, or of every place
     * when null, and returns the ids whose counts changed.
     */
    List<UUID> recomputeUsageCounts(@Param("placeIds") List<UUID> placeIds);

    /** Catalogue ids of the places named by catalogue id or by Google place id. */
    List<UUID> findIdsByReferences(@Param("ids") List<UUID> ids,
                                   @Param("googlePlaceIds") List<String> googlePlaceIds);

    /** The place's id and usage counts only; null when there is no such place. */
    Place findUsageCounts(@Param("id") UUID id);

    /** Newest places first, list columns only. */
    List<Place> findListPage(@Param("placeGroups") List<String> placeGroups,
                             @Param("publicOnly") boolean publicOnly,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    /** Keyset page of the columns the Lucene index stores, ordered by id. */
    List<Place> findSearchIndexPage(@Param("afterId") UUID afterId, @Param("limit") int limit);

    /** Single row of the columns the Lucene index stores. */
    Place findSearchIndexRow(@Param("id") UUID id);

    List<Place> findAll();

    long countAll();

    List<Place> findPage(@Param("limit") int limit, @Param("offset") int offset);

    List<Place> findFilteredPage(@Param("search") String search,
                              @Param("searchId") UUID searchId,
                              @Param("placeGroups") List<String> placeGroups,
                              @Param("subTypes") List<String> subTypes,
                              @Param("visibilityStatus") List<String> visibilityStatus,
                              @Param("trustLevel") List<String> trustLevel,
                              @Param("locationImageIds") List<UUID> locationImageIds,
                              @Param("sort") String sort,
                              @Param("descending") boolean descending,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    List<PlaceDetailRefreshCandidateResponse> findDetailRefreshCandidates(
            @Param("placeId") UUID placeId,
            @Param("includeInactive") boolean includeInactive,
            @Param("limit") int limit);

    long countFiltered(@Param("search") String search,
                    @Param("searchId") UUID searchId,
                    @Param("placeGroups") List<String> placeGroups,
                    @Param("subTypes") List<String> subTypes,
                    @Param("visibilityStatus") List<String> visibilityStatus,
                    @Param("trustLevel") List<String> trustLevel,
                    @Param("locationImageIds") List<UUID> locationImageIds);

    List<Place> findByIds(@Param("ids") List<UUID> ids);

    /** Feed cards' places: the plain columns, none of the large JSON/TEXT ones. */
    List<Place> findCardsByIds(@Param("ids") List<UUID> ids);

    /** What a saved-place list shows of each place, without the heavy JSON columns. */
    List<Place> findSavedSummaryByIds(@Param("ids") List<UUID> ids);

    List<Place> findByPlaceIds(@Param("placeIds") List<String> placeIds);

    List<Place> findActiveForAiWithinRadius(@Param("latitude") BigDecimal latitude,
                                            @Param("longitude") BigDecimal longitude,
                                            @Param("radiusKm") BigDecimal radiusKm,
                                            @Param("areaId") UUID areaId,
                                            @Param("areaLatitude") BigDecimal areaLatitude,
                                            @Param("areaLongitude") BigDecimal areaLongitude,
                                            @Param("areaRadiusKm") BigDecimal areaRadiusKm,
                                            @Param("placeGroups") List<String> placeGroups,
                                            @Param("limit") int limit);

    void delete(@Param("id") UUID id);

    int updateVerificationGeometry(@Param("id") UUID id, @Param("geoJson") String geoJson);

    int updateWardCode(@Param("id") UUID id, @Param("wardCode") String wardCode);

    int updateSubTypes(@Param("id") UUID id, @Param("subTypes") String[] subTypes);

    int assignWard(@Param("id") UUID id);

    Boolean isWithinVerificationGeometry(@Param("id") UUID id,
                                         @Param("latitude") BigDecimal latitude,
                                         @Param("longitude") BigDecimal longitude,
                                         @Param("accuracyMeters") BigDecimal accuracyMeters);

    java.util.Map<String, Object> validateGeometry(@Param("geoJson") String geoJson);
}
