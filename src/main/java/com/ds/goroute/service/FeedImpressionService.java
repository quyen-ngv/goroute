package com.ds.goroute.service;

import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.type.FeedItemType;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What each viewer had on screen in the Feed (feed_impressions, V245).
 *
 * <p>Feeds three things: the "seen" mark on the Friends tab, the seen filter of the ranked
 * feed, and the denominator of every engagement rate. The viewer's position is never stored.
 */
public interface FeedImpressionService {

    /**
     * Records one batch from the app. The same post twice in a batch counts once, keeping the
     * longer dwell and opened if either says so.
     */
    void record(UUID userId, List<FeedImpressionRequest.Item> items);

    /** Of {@code itemIds}, those {@code userId} has already seen. Empty for a guest. */
    Set<UUID> seenIds(UUID userId, FeedItemType itemType, Collection<UUID> itemIds);

    /** Deletes rows older than FEED.IMPRESSION_RETENTION_DAYS; returns how many went. */
    int purgeExpired();
}
