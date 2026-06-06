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
    public void softDelete(UUID id) {
        mediaAssetMapper.softDelete(id);
    }
}
