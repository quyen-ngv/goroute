package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.LibraryAsset;
import com.ds.goroute.mapper.LibraryAssetMapper;
import com.ds.goroute.repository.LibraryAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LibraryAssetRepositoryImpl implements LibraryAssetRepository {

    /** Keeps one IN list well under the driver's parameter limit. */
    private static final int ID_BATCH_SIZE = 500;

    private final LibraryAssetMapper libraryAssetMapper;

    @Override
    public void insert(LibraryAsset asset) {
        libraryAssetMapper.insert(asset);
    }

    @Override
    public int update(LibraryAsset asset) {
        return libraryAssetMapper.update(asset);
    }

    @Override
    public int delete(UUID id) {
        return libraryAssetMapper.delete(id);
    }

    @Override
    public Optional<LibraryAsset> findById(UUID id) {
        return Optional.ofNullable(libraryAssetMapper.findById(id));
    }

    @Override
    public Optional<LibraryAsset> findByCollectionAndKey(String collection, String assetKey) {
        return Optional.ofNullable(libraryAssetMapper.findByCollectionAndKey(collection, assetKey));
    }

    @Override
    public Map<UUID, LibraryAsset> findByIds(Collection<UUID> ids) {
        Map<UUID, LibraryAsset> byId = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return byId;
        }
        List<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        for (int from = 0; from < distinct.size(); from += ID_BATCH_SIZE) {
            List<UUID> batch = distinct.subList(from, Math.min(from + ID_BATCH_SIZE, distinct.size()));
            libraryAssetMapper.findByIds(batch).forEach(asset -> byId.put(asset.getId(), asset));
        }
        return byId;
    }

    @Override
    public List<LibraryAsset> search(String collection, String status, String q, boolean missingImage,
                                     int limit, int offset) {
        return libraryAssetMapper.search(collection, status, q, missingImage, limit, offset);
    }

    @Override
    public long count(String collection, String status, String q, boolean missingImage) {
        return libraryAssetMapper.count(collection, status, q, missingImage);
    }
}
