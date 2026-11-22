package com.ds.goroute.repository;

import com.ds.goroute.entity.CameraLook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CameraLookRepository {

    void insert(CameraLook look);

    /** @return rows updated, 0 when the look no longer exists */
    int update(CameraLook look);

    /** Moves one look of the given kind; 0 when the id is not a look of that kind. */
    int updateSortOrder(UUID id, String kind, int sortOrder, UUID updatedBy, LocalDateTime updatedAt);

    int delete(UUID id);

    Optional<CameraLook> findById(UUID id);

    Optional<CameraLook> findByKindAndKey(String kind, String lookKey);

    /** Display order: kind, sort order, id. */
    List<CameraLook> search(String kind, String status, String q, int limit);

    /** ACTIVE looks inside their date window at {@code now}, in display order. */
    List<CameraLook> findActive(LocalDateTime now, int limit);
}
