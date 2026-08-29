package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * The four numbers of MOD-08. Each one points at a specific action: a high false-positive
 * rate means downgrade a term or add an exemption; a high miss rate means the list is too
 * narrow; a growing backlog means either tighten thresholds or add reviewers.
 */
@Data
@Builder
public class ModerationMetricsResponse {

    /** Share of reviewed flags a human decided to keep. The filter was wrong that often. */
    private double falsePositiveRate;

    /** Share of reported content the automatic layers had allowed. */
    private double missRate;

    private long pendingFlags;
    private long flagsRaised;
    private long flagsKept;
    private long flagsRemoved;
    private double averageHandlingSeconds;

    /** Terms ranked by how often a reviewer kept what they flagged. */
    private List<Map<String, Object>> falsePositiveTerms;

    /** Image groups with their volume and average confidence. */
    private List<Map<String, Object>> imageCategories;

    private List<Map<String, Object>> decisionBreakdown;
}
