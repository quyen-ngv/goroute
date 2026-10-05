package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence facade over {@link LocalEventMapper}. Boolean results mean "exactly one row changed". */
public interface LocalEventRepository {

    List<LocalEvent> findPublicWithinRadius(LocalEventGeoSearchParams params);

    long countPublicWithinRadius(LocalEventGeoSearchParams params);

    Optional<LocalEvent> findPublishedById(UUID id);

    List<LocalEvent> findForAdmin(LocalEventAdminSearchParams params);

    long countForAdmin(LocalEventAdminSearchParams params);

    /** Any row, deleted or not. */
    Optional<LocalEvent> findById(UUID id);

    /** Any row with this natural key, deleted or not. */
    Optional<LocalEvent> findBySourceAndExternalId(String source, String externalId);

    Map<UUID, LocalEvent> findSummariesByIds(Collection<UUID> ids);

    List<LocalEventDuplicateCandidate> findDuplicateCandidates(LocalEventDuplicateSearchParams params);

    void insert(LocalEvent event);

    boolean updateContent(LocalEvent event, long expectedVersion);

    boolean updatePublishStatus(UUID id, long expectedVersion, String publishStatus, LocalDateTime publishedAt,
                                UUID updatedBy, LocalDateTime updatedAt);

    boolean softDelete(UUID id, UUID updatedBy, LocalDateTime updatedAt);

    void updateCoverFromImage(UUID eventId, UUID imageId);

    List<LocalEventImage> findImagesByEvent(UUID eventId);

    /** Pending image count per event id; events without pending images are absent. */
    Map<UUID, Integer> countPendingImages(Collection<UUID> eventIds);

    /** Replaces every image row of the event with {@code images} (may be empty). */
    void replaceImages(UUID eventId, List<LocalEventImage> images);

    List<LocalEventImage> findPendingImages(int maxAttempts, int limit);

    boolean markImageDone(UUID id, String url, LocalDateTime updatedAt);

    boolean markImageAttemptFailed(UUID id, String status, String failureReason, LocalDateTime updatedAt);

    int retryFailedImages(UUID eventId, LocalDateTime updatedAt);
}
