package com.ds.goroute.repository.impl;

import com.ds.goroute.dto.response.PlaceDetailRefreshCandidateResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTrendingRow;
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

    private static final int TRANSLATION_ID_BATCH_SIZE = 500;

    private final PlaceMapper placeMapper;
    private final com.ds.goroute.mapper.PlaceTranslationMapper placeTranslationMapper;

    public PlaceRepositoryImpl(PlaceMapper placeMapper,
                               com.ds.goroute.mapper.PlaceTranslationMapper placeTranslationMapper) {
        this.placeMapper = placeMapper;
        this.placeTranslationMapper = placeTranslationMapper;
    }

    @Override
    public java.util.Map<UUID, List<com.ds.goroute.entity.PlaceTranslation>> findTranslationsByPlaceIds(
            Collection<UUID> placeIds) {
        java.util.Map<UUID, List<com.ds.goroute.entity.PlaceTranslation>> byPlace = new java.util.HashMap<>();
        if (placeIds == null || placeIds.isEmpty()) {
            return byPlace;
        }
        List<UUID> distinctIds = placeIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        for (int from = 0; from < distinctIds.size(); from += TRANSLATION_ID_BATCH_SIZE) {
            List<UUID> batch = distinctIds.subList(
                    from, Math.min(from + TRANSLATION_ID_BATCH_SIZE, distinctIds.size()));
            for (com.ds.goroute.entity.PlaceTranslation translation : placeTranslationMapper.findByPlaceIds(batch)) {
                byPlace.computeIfAbsent(translation.getPlaceId(), ignored -> new java.util.ArrayList<>())
                        .add(translation);
            }
        }
        return byPlace;
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
    public List<Place> findReferencesByPlaceIds(List<String> placeIds) {
        return placeIds == null || placeIds.isEmpty() ? List.of() : placeMapper.findReferencesByPlaceIds(placeIds);
    }

    @Override
    public List<Place> findReferencesByCids(List<String> cids) {
        return cids == null || cids.isEmpty() ? List.of() : placeMapper.findReferencesByCids(cids);
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
    public List<PlaceTrendingRow> findTrendingUsage(BigDecimal latitude, BigDecimal longitude,
                                                    BigDecimal radiusMeters, List<String> placeGroups,
                                                    int days, boolean excludeAiTrips, int limit) {
        return placeMapper.findTrendingUsage(latitude, longitude, radiusMeters, placeGroups, days, excludeAiTrips, limit);
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
    public java.util.Map<UUID, String> findSearchNameHashes() {
        java.util.Map<UUID, String> hashes = new java.util.HashMap<>();
        for (com.ds.goroute.entity.PlaceSearchNameHash row : placeMapper.findSearchNameHashes()) {
            hashes.put(row.getId(), row.getNameHash());
        }
        return hashes;
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
    public List<Place> findCardsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Place> places = new java.util.ArrayList<>(ids.size());
        for (List<UUID> batch : com.google.common.collect.Lists.partition(ids, 500)) {
            places.addAll(placeMapper.findCardsByIds(batch));
        }
        return places;
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
    public List<Place> findActiveForAiWithinRadius(BigDecimal latitude, BigDecimal longitude,
                                                   BigDecimal radiusKm, List<String> placeGroups, int limit) {
        if (latitude == null || longitude == null || radiusKm == null || limit <= 0) {
            return List.of();
        }
        return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, null, null, null, null,
                placeGroups, null, limit);
    }

    @Override
    public List<Place> findActiveForAiInArea(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                             com.ds.goroute.entity.LocationImage area, List<String> placeGroups,
                                             int limit) {
        return findActiveForAiInArea(latitude, longitude, radiusKm, area, placeGroups, null, limit);
    }

    @Override
    public List<Place> findActiveForAiInArea(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                             com.ds.goroute.entity.LocationImage area, List<String> placeGroups,
                                             List<UUID> ids, int limit) {
        if (latitude == null || longitude == null || radiusKm == null || limit <= 0
                || (ids != null && ids.isEmpty())) {
            return List.of();
        }
        if (area == null || area.getId() == null || area.getLatitude() == null || area.getLongitude() == null) {
            return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, null, null, null, null,
                    placeGroups, ids, limit);
        }
        BigDecimal areaRadius = area.getCoverageRadiusKm() == null ? BigDecimal.valueOf(30) : area.getCoverageRadiusKm();
        return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, area.getId(),
                area.getLatitude(), area.getLongitude(), areaRadius, placeGroups, ids, limit);
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
    public void updateSearchAliases(UUID id, String[] searchAliases) {
        placeMapper.updateSearchAliases(id, searchAliases == null ? new String[0] : searchAliases);
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

    @Override
    public Optional<com.ds.goroute.entity.PlaceImageState> findImageState(UUID id) {
        return Optional.ofNullable(placeMapper.findImageState(id));
    }

    @Override
    public boolean recordImageCompression(UUID id, String recordJson) {
        return placeMapper.recordImageCompression(id, recordJson) == 1;
    }

    @Override
    public List<UUID> findImageCompressionPendingIds(UUID afterId, int limit) {
        return placeMapper.findImageCompressionPendingIds(afterId, limit);
    }

    @Override
    public long countImageCompressionPending() {
        return placeMapper.countImageCompressionPending();
    }

    @Override
    public List<String> findLockedFields(UUID id) {
        List<String> fields = placeMapper.findLockedFields(id);
        return fields == null ? List.of() : fields;
    }

    @Override
    public void addLockedFields(UUID id, Collection<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        placeMapper.addLockedFields(id, fields.stream().distinct().toArray(String[]::new));
    }

    @Override
    public void removeLockedField(UUID id, String field) {
        placeMapper.removeLockedField(id, field);
    }

    @Override
    public Optional<com.ds.goroute.entity.PlaceEditGuard> findEditGuard(UUID id) {
        return Optional.ofNullable(placeMapper.findEditGuard(id));
    }

    @Override
    public void updateAddress(UUID id, String address) {
        placeMapper.updateAddress(id, address);
    }

    @Override
    public void updateLocation(UUID id, BigDecimal latitude, BigDecimal longitude) {
        placeMapper.updateLocation(id, latitude, longitude);
    }

    @Override
    public void updateOpenHours(UUID id, String openHoursJson) {
        placeMapper.updateOpenHours(id, openHoursJson, openHoursJson);
    }

    @Override
    public void updatePhone(UUID id, String phone) {
        placeMapper.updatePhone(id, phone);
    }

    @Override
    public void updateWebsite(UUID id, String website) {
        placeMapper.updateWebsite(id, website);
    }

    @Override
    public void updatePriceRange(UUID id, String priceRange) {
        placeMapper.updatePriceRange(id, priceRange);
    }

    @Override
    public void updateStatusText(UUID id, String status) {
        placeMapper.updateStatusText(id, status);
    }

    @Override
    public void updateVisibility(UUID id, com.ds.goroute.type.PlaceVisibilityStatus visibility, UUID mergedIntoPlaceId) {
        placeMapper.updateVisibility(id, visibility.name(), mergedIntoPlaceId);
    }

    @Override
    public void updatePlaceGroup(UUID id, com.ds.goroute.type.PlaceGroup placeGroup) {
        placeMapper.updatePlaceGroup(id, placeGroup.name());
    }

    @Override
    public void updateAttributes(UUID id, String attributesJson) {
        placeMapper.updateAttributes(id, attributesJson);
    }
}
