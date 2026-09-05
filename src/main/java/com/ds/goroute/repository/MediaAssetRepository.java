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

    /** Trip memory rows only; other media now also carries trip/activity ids. */
    List<MediaAsset> findTripMemoriesByTripId(UUID tripId);
    List<MediaAsset> findTripMemoriesByActivityId(UUID activityId);
    int countTripMemoriesByTripId(UUID tripId);

    /** Images attached to something other than a trip or activity — an expense, say. */
    List<MediaAsset> findByEntity(String entityType, UUID entityId);
    List<MediaAsset> findByEntityType(String entityType);

    /** One query for a page of entities, so a list of expenses is not N+1. */
    List<MediaAsset> findByEntityIds(String entityType, List<UUID> entityIds);

    void updateDetails(MediaAsset mediaAsset);
    void updatePosition(MediaAsset mediaAsset);
    void updateUrl(UUID id, String url);
    void softDelete(UUID id);
    void softDeleteByEntity(String entityType, UUID entityId);
}
