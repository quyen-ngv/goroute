package com.ds.goroute.repository;

import com.ds.goroute.entity.PlaceScore;
import java.util.Optional;
import java.util.UUID;

public interface PlaceScoreRepository {
    void save(PlaceScore score);
    void update(PlaceScore score);
    Optional<PlaceScore> findByPlaceId(UUID placeId);
}
