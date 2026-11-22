package com.ds.goroute.repository;

import com.ds.goroute.entity.LibraryAsset;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LibraryAssetRepository {

    void insert(LibraryAsset asset);

    /** @return rows updated, 0 when the asset no longer exists */
    int update(LibraryAsset asset);

    int delete(UUID id);

    Optional<LibraryAsset> findById(UUID id);

    Optional<LibraryAsset> findByCollectionAndKey(String collection, String assetKey);

    /** Batch lookup for consumers resolving references; ids that do not exist are absent from the map. */
    Map<UUID, LibraryAsset> findByIds(Collection<UUID> ids);

    List<LibraryAsset> search(String collection, String status, String q, boolean missingImage, int limit, int offset);

    long count(String collection, String status, String q, boolean missingImage);
}
