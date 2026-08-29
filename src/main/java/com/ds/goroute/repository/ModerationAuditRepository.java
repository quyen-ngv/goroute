package com.ds.goroute.repository;

import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.entity.ModerationDecision;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Persistence for the moderation record that MOD-08 reads. */
public interface ModerationAuditRepository {

    int insertDecision(ModerationDecision decision);

    int insertImageResult(ImageModerationResult result);

    List<Map<String, Object>> summarizeDecisions(LocalDateTime from, LocalDateTime to);

    Map<String, Object> summarizeQueue(LocalDateTime from, LocalDateTime to);

    List<Map<String, Object>> rankFalsePositiveTerms(LocalDateTime from, int limit);

    Map<String, Object> summarizeMisses(LocalDateTime from);

    List<Map<String, Object>> rankImageCategories(LocalDateTime from);
}
