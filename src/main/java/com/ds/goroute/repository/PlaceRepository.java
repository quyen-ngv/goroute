package com.ds.goroute.repository;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {

    void insert(Place place);

    void update(Place place);

    void updateReviewRefreshMetadata(Place place);

    Optional<Place> findById(UUID id);

    Place findByPlaceId(String placeId);

    Place findByCid(String cid);

    List<String> findExistingPlaceIds(List<String> placeIds);

    List<String> findExistingCids(List<String> cids);

    Place findNearCoordinates(BigDecimal latitude, BigDecimal longitude, BigDecimal maxDistanceMeters);

    List<Place> findAll();

    long countAll();

    List<Place> findPage(int limit, int offset);

    List<Place> findFilteredPage(@Param("search") String search, @Param("placeGroups") List<String> placeGroups,
                                 @Param("visibilityStatus") List<String> visibilityStatus, @Param("trustLevel") List<String> trustLevel,
                                 @Param("locationImageIds") List<UUID> locationImageIds,
                                 @Param("sort") String sort, @Param("descending") boolean descending,
                                 @Param("limit") int limit, @Param("offset") int offset);

    List<PlaceDetailRefreshCandidateResponse> findDetailRefreshCandidates(
            UUID placeId, boolean includeInactive, int limit);

    long countFiltered(@Param("search") String search, @Param("placeGroups") List<String> placeGroups,
                       @Param("visibilityStatus") List<String> visibilityStatus, @Param("trustLevel") List<String> trustLevel,
                       @Param("locationImageIds") List<UUID> locationImageIds);

    List<Place> findByIds(List<UUID> ids);

    List<Place> findByPlaceIds(List<String> placeIds);

    List<Place> findForAiByDestination(String citySlugJson, BigDecimal latitude, BigDecimal longitude,
                                       String placeGroup, BigDecimal minRating, int limit);

    List<Place> findActiveForAiWithinRadius(BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radiusKm, List<String> placeGroups, int limit);

    void delete(UUID id);

    /** Replaces the drawn verification area; null clears it. */
    void updateVerificationGeometry(UUID id, String geoJson);

    void updateWardCode(UUID id, String wardCode);

    /** Point-in-polygon against the ward table; returns rows changed (0 outside every boundary). */
    int assignWard(UUID id);

    /**
     * Whether the point counts as inside the place's drawn area, allowing the fix's own
     * accuracy as slack. Null when the place has no drawn area.
     */
    Boolean isWithinVerificationGeometry(UUID id, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters);

    /** Validity, reason and area (km²) of a GeoJSON geometry, as PostGIS sees it. */
    GeometryValidation validateGeometry(String geoJson);

    record GeometryValidation(boolean valid, String reason, double areaKm2, String geometryType) {
    }
}
