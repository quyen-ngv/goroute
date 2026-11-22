package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.CameraLook;
import com.ds.goroute.mapper.CameraLookMapper;
import com.ds.goroute.repository.CameraLookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CameraLookRepositoryImpl implements CameraLookRepository {

    private final CameraLookMapper cameraLookMapper;

    @Override
    public void insert(CameraLook look) {
        cameraLookMapper.insert(look);
    }

    @Override
    public int update(CameraLook look) {
        return cameraLookMapper.update(look);
    }

    @Override
    public int updateSortOrder(UUID id, String kind, int sortOrder, UUID updatedBy, LocalDateTime updatedAt) {
        return cameraLookMapper.updateSortOrder(id, kind, sortOrder, updatedBy, updatedAt);
    }

    @Override
    public int delete(UUID id) {
        return cameraLookMapper.delete(id);
    }

    @Override
    public Optional<CameraLook> findById(UUID id) {
        return Optional.ofNullable(cameraLookMapper.findById(id));
    }

    @Override
    public Optional<CameraLook> findByKindAndKey(String kind, String lookKey) {
        return Optional.ofNullable(cameraLookMapper.findByKindAndKey(kind, lookKey));
    }

    @Override
    public List<CameraLook> search(String kind, String status, String q, int limit) {
        return cameraLookMapper.search(kind, status, q, limit);
    }

    @Override
    public List<CameraLook> findActive(LocalDateTime now, int limit) {
        return cameraLookMapper.findActive(now, limit);
    }
}
