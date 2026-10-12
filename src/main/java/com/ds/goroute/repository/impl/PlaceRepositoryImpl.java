package com.ds.goroute.repository.impl;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.repository.PlaceRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceMapper placeMapper;

    public PlaceRepositoryImpl(PlaceMapper placeMapper) {
        this.placeMapper = placeMapper;
    }

    @Override
    public void insert(Place place) {
        placeMapper.insert(place);
    }

    @Override
    public void update(Place place) {
        placeMapper.update(place);
    }

    @Override
    public void updateReviewRefreshMetadata(Place place) {
        placeMapper.updateReviewRefreshMetadata(place);
    }

    @Override
    public Optional<Place> findById(UUID id) {
        return Optional.ofNullable(placeMapper.findById(id));
    }

    @Override
    public Place findByPlaceId(String placeId) {
        return placeMapper.findByPlaceId(placeId);
    }

    @Override
    public Place findByCid(String cid) {
        if (cid == null || cid.isBlank()) {
            return null;
        }
        return placeMapper.findByCid(cid);
    }

    @Override
    public List<String> findExistingPlaceIds(List<String> placeIds) {
        return placeIds == null || placeIds.isEmpty() ? List.of() : placeMapper.findExistingPlaceIds(placeIds);
    }

    @Override
    public List<String> findExistingCids(List<String> cids) {
        return cids == null || cids.isEmpty() ? List.of() : placeMapper.findExistingCids(cids);
    }

    @Override
    public Place findNearCoordinates(BigDecimal latitude, BigDecimal longitude, BigDecimal maxDistanceMeters) {
        if (latitude == null || longitude == null || maxDistanceMeters == null) {
            return null;
        }
        return placeMapper.findNearCoordinates(latitude, longitude, maxDistanceMeters);
    }

    @Override
    public List<Place> findNearbyIdentityCandidates(BigDecimal latitude, BigDecimal longitude,
                                                    BigDecimal maxDistanceMeters, int limit) {
        if (latitude == null || longitude == null || maxDistanceMeters == null || limit <= 0) {
            return List.of();
        }
        return placeMapper.findNearbyIdentityCandidates(latitude, longitude, maxDistanceMeters, limit);
    }

    @Override
    public boolean existsById(UUID id) {
        return id != null && placeMapper.existsById(id);
    }

    @Override
    public Optional<Place> findGeoCodes(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(placeMapper.findGeoCodes(id));
    }

    @Override
    public List<Place> findListSummaryByIds(List<UUID> ids, boolean publicOnly) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return placeMapper.findListSummaryByIds(ids, publicOnly);
    }

    @Override
    public List<Place> findMapPinsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return placeMapper.findMapPinsByIds(ids);
    }

    @Override
    public List<UUID> recomputeUsageCounts(List<UUID> placeIds) {
        if (placeIds != null && placeIds.isEmpty()) {
            return List.of();
        }
        return placeMapper.recomputeUsageCounts(placeIds);
    }

    @Override
    public List<UUID> findIdsByReferences(Collection<UUID> ids, Collection<String> googlePlaceIds) {
        List<UUID> catalogueIds = ids == null ? List.of() : ids.stream().distinct().toList();
        List<String> googleIds = googlePlaceIds == null ? List.of() : googlePlaceIds.stream().distinct().toList();
        if (catalogueIds.isEmpty() && googleIds.isEmpty()) {
            return List.of();
        }
        return placeMapper.findIdsByReferences(catalogueIds, googleIds);
    }

    @Override
    public Optional<Place> findUsageCounts(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(placeMapper.findUsageCounts(id));
    }

    @Override
    public List<Place> findListPage(List<String> placeGroups, boolean publicOnly, int limit, int offset) {
        return placeMapper.findListPage(placeGroups, publicOnly, limit, offset);
    }

    @Override
    public List<Place> findSearchIndexPage(UUID afterId, int limit) {
        return placeMapper.findSearchIndexPage(afterId, limit);
    }

    @Override
    public Optional<Place> findSearchIndexRow(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(placeMapper.findSearchIndexRow(id));
    }

    @Override
    public List<Place> findAll() {
        return placeMapper.findAll();
    }

    @Override
    public long countAll() {
        return placeMapper.countAll();
    }

    @Override
    public List<Place> findPage(int limit, int offset) {
        return placeMapper.findPage(limit, offset);
    }

    @Override
    public List<Place> findFilteredPage(String search, UUID searchId, List<String> placeGroups, List<String> subTypes,
                                        List<String> visibilityStatus,
                                        List<String> trustLevel, List<UUID> locationImageIds,
                                        String sort, boolean descending, int limit, int offset) {
        return placeMapper.findFilteredPage(search, searchId, placeGroups, subTypes, visibilityStatus, trustLevel, locationImageIds,
                sort, descending, limit, offset);
    }

    @Override
    public List<PlaceDetailRefreshCandidateResponse> findDetailRefreshCandidates(
            UUID placeId, boolean includeInactive, int limit) {
        return placeMapper.findDetailRefreshCandidates(placeId, includeInactive, limit);
    }

    @Override
    public long countFiltered(String search, UUID searchId, List<String> placeGroups, List<String> subTypes,
                              List<String> visibilityStatus,
                              List<String> trustLevel, List<UUID> locationImageIds) {
        return placeMapper.countFiltered(search, searchId, placeGroups, subTypes, visibilityStatus, trustLevel, locationImageIds);
    }

    @Override
    public List<Place> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return placeMapper.findByIds(ids);
    }

    @Override
    public List<Place> findByPlaceIds(List<String> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        return placeMapper.findByPlaceIds(placeIds);
    }

    @Override
    public List<Place> findForAiByDestination(String citySlugJson, BigDecimal latitude, BigDecimal longitude,
                                              String placeGroup, BigDecimal minRating, int limit) {
        return placeMapper.findForAiByDestination(
                citySlugJson, latitude, longitude, placeGroup, minRating, limit);
    }

    @Override
    public List<Place> findActiveForAiWithinRadius(BigDecimal latitude, BigDecimal longitude,
                                                   BigDecimal radiusKm, List<String> placeGroups, int limit) {
        if (latitude == null || longitude == null || radiusKm == null || limit <= 0) {
            return List.of();
        }
        return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, null, null, null, null,
                placeGroups, limit);
    }

    @Override
    public List<Place> findActiveForAiInArea(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                             com.ds.goroute.entity.LocationImage area, List<String> placeGroups,
                                             int limit) {
        if (area == null || area.getId() == null || area.getLatitude() == null || area.getLongitude() == null) {
            return findActiveForAiWithinRadius(latitude, longitude, radiusKm, placeGroups, limit);
        }
        if (latitude == null || longitude == null || radiusKm == null || limit <= 0) {
            return List.of();
        }
        BigDecimal areaRadius = area.getCoverageRadiusKm() == null ? BigDecimal.valueOf(30) : area.getCoverageRadiusKm();
        return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, area.getId(),
                area.getLatitude(), area.getLongitude(), areaRadius, placeGroups, limit);
    }

    @Override
    public void delete(UUID id) {
        placeMapper.delete(id);
    }

    @Override
    public void updateVerificationGeometry(UUID id, String geoJson) {
        placeMapper.updateVerificationGeometry(id, geoJson);
    }

    @Override
    public void updateWardCode(UUID id, String wardCode) {
        placeMapper.updateWardCode(id, wardCode);
    }

    @Override
    public void updateSubTypes(UUID id, String[] subTypes) {
        placeMapper.updateSubTypes(id, subTypes == null ? new String[0] : subTypes);
    }

    @Override
    public int assignWard(UUID id) {
        return placeMapper.assignWard(id);
    }

    @Override
    public Boolean isWithinVerificationGeometry(UUID id, BigDecimal latitude, BigDecimal longitude,
                                                BigDecimal accuracyMeters) {
        return placeMapper.isWithinVerificationGeometry(id, latitude, longitude, accuracyMeters);
    }

    @Override
    public GeometryValidation validateGeometry(String geoJson) {
        java.util.Map<String, Object> row = placeMapper.validateGeometry(geoJson);
        Object area = row.get("area_km2");
        return new GeometryValidation(
                Boolean.TRUE.equals(row.get("valid")),
                row.get("reason") == null ? null : row.get("reason").toString(),
                area instanceof Number number ? number.doubleValue() : 0d,
                row.get("geometry_type") == null ? null : row.get("geometry_type").toString());
    }
}
