package com.ds.goroute.service;

import com.ds.goroute.dto.request.ModerationPreviewRequest;
import com.ds.goroute.dto.request.ResolveModerationFlagRequest;
import com.ds.goroute.dto.request.UpsertModerationTermRequest;
import com.ds.goroute.dto.response.ModerationFlagResponse;
import com.ds.goroute.dto.response.ModerationMetricsResponse;
import com.ds.goroute.dto.response.ModerationPreviewResponse;
import com.ds.goroute.dto.response.ModerationTermAuditResponse;
import com.ds.goroute.dto.response.ModerationTermResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operator side of moderation: the term list (MOD-02), the queue (MOD-06), the numbers (MOD-08). */
public interface ModerationAdminService {

    List<ModerationTermResponse> listTerms(String query, String category, Boolean isExemption,
                                           Boolean active, int page, int size);

    long countTerms(String query, String category, Boolean isExemption, Boolean active);

    ModerationTermResponse createTerm(UUID actorId, UpsertModerationTermRequest request);

    ModerationTermResponse updateTerm(UUID actorId, UUID id, UpsertModerationTermRequest request);

    void deleteTerm(UUID actorId, UUID id);

    List<ModerationTermAuditResponse> termAudit(UUID id, int limit);

    /** Tries a passage against the current list without changing anything. */
    ModerationPreviewResponse preview(ModerationPreviewRequest request);

    List<ModerationFlagResponse> queue(String status, String contentType, String category,
                                       String source, int page, int size);

    long countQueue(String status, String contentType, String category, String source);

    /**
     * Records the reviewer decision. REMOVED hides the content and notifies its author;
     * KEPT is stored as evidence that the filter was wrong.
     */
    ModerationFlagResponse resolve(UUID actorId, UUID flagId, ResolveModerationFlagRequest request);

    ModerationMetricsResponse metrics(int days);
}
