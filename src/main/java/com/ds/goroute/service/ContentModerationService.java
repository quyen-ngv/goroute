package com.ds.goroute.service;

import com.ds.goroute.dto.request.ReportContentRequest;
import com.ds.goroute.dto.response.ContentReportResponse;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationFlagSource;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that happens to a piece of content after the filter has decided: flags,
 * user reports and takedowns (MOD-06, SOC-06a).
 *
 * <p>All three sources feed one queue, and content is addressed by (type, id) rather
 * than by a foreign key, so adding a content kind never means adding a table.
 */
public interface ContentModerationService {

    /**
     * Raises a flag for a human to look at, or folds into the flag that already exists
     * for the same content, source and category.
     */
    void flag(ModeratedContentType contentType,
              UUID contentId,
              UUID ownerId,
              ModerationFlagSource source,
              ModerationVerdict verdict,
              String contextSnapshot);

    /**
     * Records a user report. Reporting the same content twice is rejected, and no number
     * of reports ever removes content on its own -- they only raise its priority.
     */
    ContentReportResponse report(UUID reporterId, ReportContentRequest request);

    /** True when the content is currently hidden by a moderation takedown. */
    boolean isTakenDown(ModeratedContentType contentType, UUID contentId);

    /**
     * Bulk variant for list endpoints: returns the subset of ids that are taken down, so
     * a feed can be filtered in one query instead of one per row.
     */
    Set<UUID> takenDownIds(ModeratedContentType contentType, Collection<UUID> contentIds);
}
