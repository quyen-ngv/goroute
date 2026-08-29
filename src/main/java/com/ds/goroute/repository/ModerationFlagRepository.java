package com.ds.goroute.repository;

import com.ds.goroute.entity.ContentReport;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.ModerationFlag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for the shared review queue, user reports and takedowns. */
public interface ModerationFlagRepository {

    int upsert(ModerationFlag flag);

    Optional<ModerationFlag> findById(UUID id);

    Optional<ModerationFlag> findOpen(String contentType, UUID contentId, String source, String category);

    List<ModerationFlag> findQueue(String status, String contentType, String category,
                                   String source, int limit, int offset);

    long countQueue(String status, String contentType, String category, String source);

    int resolve(UUID id, String status, UUID reviewedBy, String resolutionNote);

    int insertReport(ContentReport report);

    List<ContentReport> findReportsForContent(String contentType, UUID contentId);

    int resolveReportsForFlag(UUID flagId, String status);

    int insertTakedown(ContentTakedown takedown);

    Optional<ContentTakedown> findActiveTakedown(String contentType, UUID contentId);

    List<UUID> findActiveTakedownIds(String contentType, List<UUID> contentIds);

    int restoreTakedown(String contentType, UUID contentId, UUID restoredBy);
}
