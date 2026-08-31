package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.mapper.MediaAssetMapper;
import com.ds.goroute.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public List<MediaAsset> findByEntity(String entityType, UUID entityId) {
        return mediaAssetMapper.selectByEntity(entityType, entityId);
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
    public void softDelete(UUID id) {
        mediaAssetMapper.softDelete(id);
    }
}
