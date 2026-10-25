package com.ds.goroute.feed.index;

import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.FeedWeightsProvider;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.mapper.FeedItemMapper;
import com.ds.goroute.mapper.FeedViewerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps feed_items current (V249). Three steps, all safe to repeat and to interrupt:
 *
 * <ol>
 *   <li>{@link #drainQueue}: posts the triggers queued are re-read from their source tables.
 *   <li>{@link #refreshStats}: rows marked stats_dirty (an interaction, a new viewer, a
 *       re-index) get their counts recounted and their static score recomputed.
 *   <li>{@link #reconcile} (nightly): recent and long-unscored rows are queued again, which
 *       repairs anything a trigger could not see (a trust score that moved, a weight change).
 * </ol>
 *
 * Every statement commits on its own; a run cut short leaves the queue and the dirty flags
 * for the next one. The backend runs as one instance by design, so there is no lock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedIndexService {

    static final int QUEUE_BATCH = 500;
    static final int STATS_BATCH = 1000;
    /** One run stops starting new batches after this, so a big backfill spreads over runs. */
    static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final FeedItemMapper itemMapper;
    private final FeedViewerMapper viewerMapper;
    private final FeedWeightsProvider weights;

    private volatile LocalDateTime lastImpressionScan = LocalDateTime.now().minusMinutes(10);

    /** Returns how many queued posts were indexed. */
    public int drainQueue() {
        Instant stopAt = Instant.now().plus(RUN_BUDGET);
        int total = 0;
        while (Instant.now().isBefore(stopAt)) {
            List<FeedKey> batch = itemMapper.selectQueued(QUEUE_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            Map<String, List<UUID>> idsByType = batch.stream().collect(Collectors.groupingBy(
                    FeedKey::getItemType, Collectors.mapping(FeedKey::getItemId, Collectors.toList())));
            idsByType.forEach(this::index);
            itemMapper.deleteQueued(batch);
            total += batch.size();
            if (batch.size() < QUEUE_BATCH) {
                break;
            }
        }
        return total;
    }

    private void index(String type, List<UUID> ids) {
        switch (type) {
            case "CHECKIN" -> itemMapper.upsertCheckins(ids);
            case "REVIEW" -> itemMapper.upsertReviews(ids);
            case "TRIP" -> itemMapper.upsertTrips(ids);
            default -> {
                return;
            }
        }
        itemMapper.deleteOrphans(type, ids);
    }

    /** Returns how many rows were rescored. */
    public int refreshStats() {
        // Overlap the previous scan by a minute: an impression written while it ran is caught.
        LocalDateTime scanFrom = lastImpressionScan.minusMinutes(1);
        lastImpressionScan = LocalDateTime.now();
        itemMapper.markImpressionsDirty(scanFrom);

        FeedWeights w = weights.get();
        Instant stopAt = Instant.now().plus(RUN_BUDGET);
        int total = 0;
        while (Instant.now().isBefore(stopAt)) {
            List<FeedKey> dirty = itemMapper.selectDirty(STATS_BATCH);
            if (dirty.isEmpty()) {
                break;
            }
            itemMapper.refreshCounts(dirty);
            List<FeedItemRow> rows = itemMapper.selectByKeys(dirty);
            List<FeedItemRow> scored = new ArrayList<>(rows.size());
            for (FeedItemRow row : rows) {
                row.setQualityScore((float) FeedItemScoring.quality(row, w));
                row.setEngagementScore((float) FeedItemScoring.staticScore(row, w));
                row.setRecommendOk(FeedItemScoring.recommendOk(row, w));
                scored.add(row);
            }
            if (!scored.isEmpty()) {
                itemMapper.updateScores(scored);
            }
            total += dirty.size();
            if (dirty.size() < STATS_BATCH) {
                break;
            }
        }
        return total;
    }

    /** Nightly: queue the last 60 days and anything not rescored for a week. */
    public int reconcile() {
        LocalDateTime now = LocalDateTime.now();
        return itemMapper.enqueueForReconcile(now.minusDays(60), now.minusDays(7));
    }

    /**
     * Nightly: rebuild who each viewer is close to, from the last 90 days. One transaction, so
     * the table is never seen empty. At T2 scale (.docs/FEED_RANKING_DESIGN_2026-10-22.md §6)
     * this becomes per-viewer batches over recently active users.
     */
    @Transactional
    public int rebuildAffinity() {
        FeedWeights w = weights.get();
        String p = "affinityJob.";
        viewerMapper.deleteAllAffinity();
        return viewerMapper.insertAffinity(
                LocalDateTime.now().minusDays(w.i(p + "windowDays", 90)),
                w.d(p + "halfLifeDays", 30),
                w.d(p + "like", 1.0),
                w.d(p + "comment", 3.0),
                w.d(p + "save", 4.0),
                w.d(p + "open", 0.5),
                w.d(p + "dwell", 0.3),
                w.i(p + "dwellMs", 5000),
                w.d(p + "minScore", 0.5),
                w.i(p + "perViewer", 300));
    }

    public int queued() {
        return itemMapper.countQueued();
    }
}
