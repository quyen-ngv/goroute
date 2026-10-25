package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.domain.LocalEventImageCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** SQL for local events and their images (resources/mapper/LocalEventMapper.xml). */
@Mapper
public interface LocalEventMapper {

    // --- public ---------------------------------------------------------------------------
    List<LocalEvent> findPublicWithinRadius(@Param("p") LocalEventGeoSearchParams params);

    long countPublicWithinRadius(@Param("p") LocalEventGeoSearchParams params);

    LocalEvent findPublishedById(@Param("id") UUID id);

    List<LocalEvent> findPublicSummariesByIds(@Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    // --- Feed search index ----------------------------------------------------------------
    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexPage(@Param("afterId") UUID afterId,
                                                                          @Param("limit") int limit);

    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexChanges(
            @Param("afterUpdatedAt") LocalDateTime afterUpdatedAt,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    LocalDateTime selectSearchIndexMaxUpdatedAt();

    // --- admin ----------------------------------------------------------------------------
    List<LocalEvent> findForAdmin(@Param("p") LocalEventAdminSearchParams params);

    long countForAdmin(@Param("p") LocalEventAdminSearchParams params);

    /** Any row, deleted or not. */
    LocalEvent findById(@Param("id") UUID id);

    /** Any row with this natural key, deleted or not. */
    LocalEvent findBySourceAndExternalId(@Param("source") String source, @Param("externalId") String externalId);

    List<LocalEvent> findSummariesByIds(@Param("ids") Collection<UUID> ids);

    List<LocalEventDuplicateCandidate> findDuplicateCandidates(@Param("p") LocalEventDuplicateSearchParams params);

    int insert(LocalEvent event);

    /** Content update guarded by {@code data_version}; never touches publish status. */
    int updateContent(@Param("e") LocalEvent event, @Param("expectedVersion") long expectedVersion);

    int updatePublishStatus(@Param("id") UUID id,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("publishStatus") String publishStatus,
                            @Param("publishedAt") LocalDateTime publishedAt,
                            @Param("updatedBy") UUID updatedBy,
                            @Param("updatedAt") LocalDateTime updatedAt);

    int softDelete(@Param("id") UUID id,
                   @Param("updatedBy") UUID updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Sets the cover from an image, but only while that image is still the event's DONE
     * position-0 image. Does not bump data_version: the job must not break a console edit.
     */
    int updateCoverFromImage(@Param("eventId") UUID eventId, @Param("imageId") UUID imageId);

    // --- images ---------------------------------------------------------------------------
    List<LocalEventImage> findImagesByEvent(@Param("eventId") UUID eventId);

    List<LocalEventImageCount> countPendingImages(@Param("eventIds") Collection<UUID> eventIds);

    int insertImages(@Param("images") List<LocalEventImage> images);

    int deleteImagesByEvent(@Param("eventId") UUID eventId);

    List<LocalEventImage> findPendingImages(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);

    /** Only flips a row that is still PENDING (it may have been replaced meanwhile). */
    int markImageDone(@Param("id") UUID id, @Param("url") String url, @Param("updatedAt") LocalDateTime updatedAt);

    /** attempts + 1; {@code status} PENDING to retry later or FAILED to give up. */
    int markImageAttemptFailed(@Param("id") UUID id,
                               @Param("status") String status,
                               @Param("failureReason") String failureReason,
                               @Param("updatedAt") LocalDateTime updatedAt);

    int retryFailedImages(@Param("eventId") UUID eventId, @Param("updatedAt") LocalDateTime updatedAt);
}
