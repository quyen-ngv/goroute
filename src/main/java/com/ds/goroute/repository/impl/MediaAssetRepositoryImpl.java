package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.mapper.MediaAssetMapper;
import com.ds.goroute.repository.MediaAssetRepository;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MediaAssetRepositoryImpl implements MediaAssetRepository {
    private final MediaAssetMapper mediaAssetMapper;

    @Override
    public void insert(MediaAsset mediaAsset) {
        mediaAssetMapper.insert(mediaAsset);
    }

    @Override
    public Optional<MediaAsset> findById(UUID id) {
        return Optional.ofNullable(mediaAssetMapper.selectById(id));
    }

    @Override
    public List<MediaAsset> findByTripId(UUID tripId) {
        return mediaAssetMapper.selectByTripId(tripId);
    }

    @Override
    public List<MediaAsset> findByActivityId(UUID activityId) {
        return mediaAssetMapper.selectByActivityId(activityId);
    }

    @Override
    public int countByTripId(UUID tripId) {
        return mediaAssetMapper.countByTripId(tripId);
    }

    @Override
    public List<MediaAsset> findTripMemoriesByTripId(UUID tripId) {
        return mediaAssetMapper.selectTripMemoriesByTripId(tripId);
    }

    @Override
    public List<MediaAsset> findTripMemoriesByActivityId(UUID activityId) {
        return mediaAssetMapper.selectTripMemoriesByActivityId(activityId);
    }

    @Override
    public int countTripMemoriesByTripId(UUID tripId) {
        return mediaAssetMapper.countTripMemoriesByTripId(tripId);
    }

    @Override
    public List<MediaAsset> findByEntity(String entityType, UUID entityId) {
        return mediaAssetMapper.selectByEntity(entityType, entityId);
    }

    @Override
    public List<MediaAsset> findByEntityType(String entityType) {
        return mediaAssetMapper.selectByEntityType(entityType);
    }

    @Override
    public List<MediaAsset> findByEntityIds(String entityType, List<UUID> entityIds) {
        // An empty IN () is a syntax error, and the answer is knowable here.
        if (entityIds == null || entityIds.isEmpty()) return List.of();
        return mediaAssetMapper.selectByEntityIds(entityType, entityIds);
    }

    @Override
    public void softDeleteByEntity(String entityType, UUID entityId) {
        mediaAssetMapper.softDeleteByEntity(entityType, entityId);
    }

    @Override
    public void updateDetails(MediaAsset mediaAsset) {
        mediaAssetMapper.updateDetails(mediaAsset);
    }

    @Override
    public void updatePosition(MediaAsset mediaAsset) {
        mediaAssetMapper.updatePosition(mediaAsset);
    }

    @Override
    public void updateUrl(UUID id, String url) {
        mediaAssetMapper.updateUrl(id, url);
    }

    @Override
    public void softDelete(UUID id) {
        mediaAssetMapper.softDelete(id);
    }

    /** Ids per IN list of the trip card batch queries. */
    private static final int TRIP_ID_BATCH_SIZE = 500;

    @Override
    public Map<UUID, List<MediaAsset>> findTripMemoryPreviewsByTripIds(Collection<UUID> tripIds, int perTrip) {
        List<UUID> ids = distinctTripIds(tripIds);
        if (ids.isEmpty() || perTrip <= 0) {
            return Map.of();
        }
        Map<UUID, List<MediaAsset>> byTrip = new LinkedHashMap<>();
        for (List<UUID> batch : Lists.partition(ids, TRIP_ID_BATCH_SIZE)) {
            for (MediaAsset asset : mediaAssetMapper.selectTripMemoryPreviewsByTripIds(batch, perTrip)) {
                byTrip.computeIfAbsent(asset.getTripId(), ignored -> new ArrayList<>()).add(asset);
            }
        }
        return byTrip;
    }

    @Override
    public Map<UUID, Integer> countTripMemoriesByTripIds(Collection<UUID> tripIds) {
        List<UUID> ids = distinctTripIds(tripIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (List<UUID> batch : Lists.partition(ids, TRIP_ID_BATCH_SIZE)) {
            for (TripCount row : mediaAssetMapper.countTripMemoriesByTripIds(batch)) {
                counts.put(row.getTripId(), row.getItemCount() == null ? 0 : row.getItemCount());
            }
        }
        return counts;
    }

    private static List<UUID> distinctTripIds(Collection<UUID> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return List.of();
        }
        return tripIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
