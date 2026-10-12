package com.ds.goroute.repository;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
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

    /**
     * Up to {@code limit} places within the radius, nearest first, with only the identity
     * columns (id, placeId, cid, title, address, latitude, longitude, distance in metres).
     * Load the full row with {@link #findById} once one is chosen.
     */
    List<Place> findNearbyIdentityCandidates(BigDecimal latitude, BigDecimal longitude,
                                             BigDecimal maxDistanceMeters, int limit);

    boolean existsById(UUID id);

    /** Ward and province codes of one place; empty when the place does not exist. */
    Optional<Place> findGeoCodes(UUID id);

    /**
     * List-card columns for the ids, in no particular order. {@code publicOnly} keeps only
     * places a guest may see (visibility and lifecycle ACTIVE).
     */
    List<Place> findListSummaryByIds(List<UUID> ids, boolean publicOnly);

    /** Map-pin columns for the ids that a guest may see, in no particular order. */
    List<Place> findMapPinsByIds(List<UUID> ids);

    /**
     * Recounts the usage counts (V225) of {@code placeIds}, or of every place when null, and
     * returns the ids whose counts changed.
     */
    List<UUID> recomputeUsageCounts(List<UUID> placeIds);

    /** Catalogue ids of the places named by catalogue id or by Google place id. */
    List<UUID> findIdsByReferences(Collection<UUID> ids, Collection<String> googlePlaceIds);

    /** The place's usage counts (V225); empty when there is no such place. */
    Optional<Place> findUsageCounts(UUID id);

    /** Newest first ({@code created_at DESC, id DESC}), list-card columns only. */
    List<Place> findListPage(List<String> placeGroups, boolean publicOnly, int limit, int offset);

    /** Keyset page (ids after {@code afterId}, ascending) of the columns the search index stores. */
    List<Place> findSearchIndexPage(UUID afterId, int limit);

    Optional<Place> findSearchIndexRow(UUID id);

    List<Place> findAll();

    long countAll();

    List<Place> findPage(int limit, int offset);

    List<Place> findFilteredPage(@Param("search") String search, @Param("searchId") UUID searchId,
                                 @Param("placeGroups") List<String> placeGroups,
                                 @Param("subTypes") List<String> subTypes,
                                 @Param("visibilityStatus") List<String> visibilityStatus, @Param("trustLevel") List<String> trustLevel,
                                 @Param("locationImageIds") List<UUID> locationImageIds,
                                 @Param("sort") String sort, @Param("descending") boolean descending,
                                 @Param("limit") int limit, @Param("offset") int offset);

    List<PlaceDetailRefreshCandidateResponse> findDetailRefreshCandidates(
            UUID placeId, boolean includeInactive, int limit);

    long countFiltered(@Param("search") String search, @Param("searchId") UUID searchId,
                       @Param("placeGroups") List<String> placeGroups,
                       @Param("subTypes") List<String> subTypes,
                       @Param("visibilityStatus") List<String> visibilityStatus, @Param("trustLevel") List<String> trustLevel,
                       @Param("locationImageIds") List<UUID> locationImageIds);

    List<Place> findByIds(List<UUID> ids);

    List<Place> findByPlaceIds(List<String> placeIds);

    List<Place> findForAiByDestination(String citySlugJson, BigDecimal latitude, BigDecimal longitude,
                                       String placeGroup, BigDecimal minRating, int limit);

    List<Place> findActiveForAiWithinRadius(BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radiusKm, List<String> placeGroups, int limit);

    /**
     * {@link #findActiveForAiWithinRadius} restricted to one tourist area; a null area means no
     * area restriction (a destination outside every curated area, e.g. abroad).
     */
    List<Place> findActiveForAiInArea(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                      com.ds.goroute.entity.LocationImage area, List<String> placeGroups, int limit);

    void delete(UUID id);

    /** Replaces the drawn verification area; null clears it. */
    void updateVerificationGeometry(UUID id, String geoJson);

    void updateWardCode(UUID id, String wardCode);

    /** Replaces the place's sub-types; the generic {@link #update} never touches them. */
    void updateSubTypes(UUID id, String[] subTypes);

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
