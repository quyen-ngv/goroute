package com.ds.goroute.repository;

import com.ds.goroute.entity.Place;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {

    void insert(Place place);

    void update(Place place);

    Optional<Place> findById(UUID id);

    Place findByPlaceId(String placeId);

    List<Place> findAll();

    long countAll();

    List<Place> findPage(int limit, int offset);

    List<Place> findByIds(List<UUID> ids);

    List<Place> findNearby(String keyword, BigDecimal latitude, BigDecimal longitude, BigDecimal radius,
                          String category, List<String> placeGroups, BigDecimal minRating,
                          int limit, int offset);

    List<Place> findNearbyExtended(String keyword, BigDecimal latitude, BigDecimal longitude, BigDecimal radius,
                                   String category, List<String> placeGroups, BigDecimal minRating, String citySlugJson,
                                   List<UUID> foodIds, Boolean excludeLinkedFoodPlaces, int limit, int offset);

    void delete(UUID id);
}
