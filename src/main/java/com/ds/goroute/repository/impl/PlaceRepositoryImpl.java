package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.repository.PlaceRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
    public List<Place> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return placeMapper.findByIds(ids);
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
        return placeMapper.findActiveForAiWithinRadius(latitude, longitude, radiusKm, placeGroups, limit);
    }

    @Override
    public void delete(UUID id) {
        placeMapper.delete(id);
    }
}
