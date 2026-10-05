package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.domain.LocalEventImageCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LocalEventRepositoryImpl implements LocalEventRepository {

    private final LocalEventMapper mapper;

    @Override
    public List<LocalEvent> findPublicWithinRadius(LocalEventGeoSearchParams params) {
        return mapper.findPublicWithinRadius(params);
    }

    @Override
    public long countPublicWithinRadius(LocalEventGeoSearchParams params) {
        return mapper.countPublicWithinRadius(params);
    }

    @Override
    public Optional<LocalEvent> findPublishedById(UUID id) {
        return Optional.ofNullable(mapper.findPublishedById(id));
    }

    @Override
    public List<LocalEvent> findForAdmin(LocalEventAdminSearchParams params) {
        return mapper.findForAdmin(params);
    }

    @Override
    public long countForAdmin(LocalEventAdminSearchParams params) {
        return mapper.countForAdmin(params);
    }

    @Override
    public Optional<LocalEvent> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<LocalEvent> findBySourceAndExternalId(String source, String externalId) {
        return Optional.ofNullable(mapper.findBySourceAndExternalId(source, externalId));
    }

    @Override
    public Map<UUID, LocalEvent> findSummariesByIds(Collection<UUID> ids) {
        Map<UUID, LocalEvent> byId = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return byId;
        }
        for (LocalEvent event : mapper.findSummariesByIds(ids)) {
            byId.put(event.getId(), event);
        }
        return byId;
    }

    @Override
    public List<LocalEventDuplicateCandidate> findDuplicateCandidates(LocalEventDuplicateSearchParams params) {
        return mapper.findDuplicateCandidates(params);
    }

    @Override
    public void insert(LocalEvent event) {
        mapper.insert(event);
    }

    @Override
    public boolean updateContent(LocalEvent event, long expectedVersion) {
        return mapper.updateContent(event, expectedVersion) == 1;
    }

    @Override
    public boolean updatePublishStatus(UUID id, long expectedVersion, String publishStatus,
                                       LocalDateTime publishedAt, UUID updatedBy, LocalDateTime updatedAt) {
        return mapper.updatePublishStatus(id, expectedVersion, publishStatus, publishedAt, updatedBy, updatedAt) == 1;
    }

    @Override
    public boolean softDelete(UUID id, UUID updatedBy, LocalDateTime updatedAt) {
        return mapper.softDelete(id, updatedBy, updatedAt) == 1;
    }

    @Override
    public void updateCoverFromImage(UUID eventId, UUID imageId) {
        mapper.updateCoverFromImage(eventId, imageId);
    }

    @Override
    public List<LocalEventImage> findImagesByEvent(UUID eventId) {
        return mapper.findImagesByEvent(eventId);
    }

    @Override
    public Map<UUID, Integer> countPendingImages(Collection<UUID> eventIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return counts;
        }
        for (LocalEventImageCount count : mapper.countPendingImages(eventIds)) {
            counts.put(count.getEventId(), count.getImageCount());
        }
        return counts;
    }

    @Override
    public void replaceImages(UUID eventId, List<LocalEventImage> images) {
        mapper.deleteImagesByEvent(eventId);
        if (images != null && !images.isEmpty()) {
            mapper.insertImages(images);
        }
    }

    @Override
    public List<LocalEventImage> findPendingImages(int maxAttempts, int limit) {
        return mapper.findPendingImages(maxAttempts, limit);
    }

    @Override
    public boolean markImageDone(UUID id, String url, LocalDateTime updatedAt) {
        return mapper.markImageDone(id, url, updatedAt) == 1;
    }

    @Override
    public boolean markImageAttemptFailed(UUID id, String status, String failureReason, LocalDateTime updatedAt) {
        return mapper.markImageAttemptFailed(id, status, failureReason, updatedAt) == 1;
    }

    @Override
    public int retryFailedImages(UUID eventId, LocalDateTime updatedAt) {
        return mapper.retryFailedImages(eventId, updatedAt);
    }
}
