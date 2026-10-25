package com.ds.goroute.mapper;

import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * feed_items and feed_index_queue (V249): the flat index of every post the Feed can show,
 * the queue that keeps it current, and the candidate reads of the ranked feed.
 */
@Mapper
public interface FeedItemMapper {

    // --- index maintenance ------------------------------------------------------------

    /** The oldest queued posts, with the time each was queued. */
    List<FeedKey> selectQueued(@Param("limit") int limit);

    /** Removes the given queue rows, unless re-queued after they were read. */
    int deleteQueued(@Param("keys") Collection<FeedKey> keys);

    /** Re-reads these check-ins from their source tables into feed_items. */
    int upsertCheckins(@Param("ids") Collection<UUID> ids);

    int upsertReviews(@Param("ids") Collection<UUID> ids);

    int upsertTrips(@Param("ids") Collection<UUID> ids);

    /** Drops rows whose source post no longer exists at all (a hard-deleted review). */
    int deleteOrphans(@Param("itemType") String itemType, @Param("ids") Collection<UUID> ids);

    /** Marks posts with a new viewer since {@code since} so their impression count is recounted. */
    int markImpressionsDirty(@Param("since") LocalDateTime since);

    List<FeedKey> selectDirty(@Param("limit") int limit);

    /** Recounts likes, comments, saves, impressions, hides and reports of these posts. */
    int refreshCounts(@Param("keys") Collection<FeedKey> keys);

    List<FeedItemRow> selectByKeys(@Param("keys") Collection<FeedKey> keys);

    /** Writes the scores computed in Java and clears stats_dirty. */
    int updateScores(@Param("rows") Collection<FeedItemRow> rows);

    /** Night reconcile: queues recent posts and rows not rescored for a week. */
    int enqueueForReconcile(@Param("recentSince") LocalDateTime recentSince,
                            @Param("staleBefore") LocalDateTime staleBefore);

    int countQueued();

    // --- candidates -------------------------------------------------------------------

    List<FeedItemRow> selectCandidates(@Param("q") FeedCandidateQuery query);
}
