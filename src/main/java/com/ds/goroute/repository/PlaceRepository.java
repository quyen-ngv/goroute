package com.ds.goroute.repository;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;

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

    List<Place> findFilteredPage(String search, List<String> placeGroups, int limit, int offset);

    List<PlaceDetailRefreshCandidateResponse> findDetailRefreshCandidates(
            UUID placeId, boolean includeInactive, int limit);

    long countFiltered(String search, List<String> placeGroups);

    List<Place> findByIds(List<UUID> ids);

    List<Place> findByPlaceIds(List<String> placeIds);

    List<Place> findForAiByDestination(String citySlugJson, BigDecimal latitude, BigDecimal longitude,
                                       String placeGroup, BigDecimal minRating, int limit);

    List<Place> findActiveForAiWithinRadius(BigDecimal latitude, BigDecimal longitude,
                                            BigDecimal radiusKm, List<String> placeGroups, int limit);

    void delete(UUID id);
}
