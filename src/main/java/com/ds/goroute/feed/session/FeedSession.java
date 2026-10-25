package com.ds.goroute.feed.session;

import com.ds.goroute.dto.response.FeedForYouPageResponse;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.model.FeedKey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One ranking, kept while the viewer scrolls it (design §3.5): pages are slices of
 * {@link #ranked}, so the order never moves under the viewer and nothing is scored twice.
 *
 * @param servedBefore every post earlier sessions of this scroll already handed out, so a
 *                     continuation never repeats one
 * @param explain      score breakdown by "TYPE:id"; only kept for debug requests
 */
public record FeedSession(
        String id,
        UUID viewerId,
        String filterKey,
        List<FeedKey> ranked,
        Set<String> servedBefore,
        FeedTypes.FillLevel fillLevel,
        FeedForYouPageResponse.Context context,
        boolean debug,
        Map<String, FeedForYouPageResponse.Rank> explain) {

    /** The ranking was cut at its size cap, so there may be more to rank after it. */
    public boolean full(int sessionSize) {
        return ranked.size() >= sessionSize;
    }
}
