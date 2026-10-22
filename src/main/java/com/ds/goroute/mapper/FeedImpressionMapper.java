package com.ds.goroute.mapper;

import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.type.FeedItemType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** feed_impressions (V245): one row per (viewer, post) the viewer had on screen. */
@Mapper
public interface FeedImpressionMapper {

    /**
     * Records one batch of sightings in a single statement. A post seen before gets its
     * {@code seen_count} raised, its {@code last_seen_at} moved to now, the longer dwell and
     * {@code opened} once ever opened. {@code items} must hold each (type, id) once.
     */
    int upsertBatch(@Param("userId") UUID userId,
                    @Param("items") Collection<FeedImpressionRequest.Item> items);

    /** Of {@code itemIds}, the ones this viewer has a row for. For marking a page in one query. */
    List<UUID> findSeenIds(@Param("userId") UUID userId,
                           @Param("itemType") FeedItemType itemType,
                           @Param("itemIds") Collection<UUID> itemIds);

    /** Deletes up to {@code limit} rows last seen before {@code cutoff}; returns how many went. */
    int deleteSeenBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
