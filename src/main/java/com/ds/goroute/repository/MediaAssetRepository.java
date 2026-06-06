package com.ds.goroute.repository;

import com.ds.goroute.entity.MediaAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository {
    void insert(MediaAsset mediaAsset);
    Optional<MediaAsset> findById(UUID id);
    List<MediaAsset> findByTripId(UUID tripId);
    List<MediaAsset> findByActivityId(UUID activityId);
    int countByTripId(UUID tripId);
    void softDelete(UUID id);
}
