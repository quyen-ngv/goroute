package com.ds.goroute.feed.source;

import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import com.ds.goroute.feed.model.FeedCandidateQuery;

import java.time.LocalDateTime;

/**
 * One way of finding posts for a viewer (design §3.2). A source only narrows the shared base
 * query (viewer exclusions and chips) with its own selector, window, order and cap, so every
 * source is one index scan with a LIMIT whatever the size of feed_items. Adding a source is
 * adding a bean.
 */
public interface CandidateSource {

    FeedTypes.SourceKind kind();

    /** Whether this source has anything to select by for this viewer and request. */
    boolean applies(ViewerContext context, FeedFilterSpec spec);

    /** {@code base} narrowed to this source; null to skip it at this level. */
    FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w,
                             FeedTypes.FillLevel level);

    /** Standard window of the source, widened from L2 and dropped from L4. */
    static LocalDateTime since(FeedTypes.SourceKind kind, FeedWeights w, FeedTypes.FillLevel level,
                               double defaultDays) {
        if (level.atLeast(FeedTypes.FillLevel.L4)) {
            return null;
        }
        double days = w.d("windowsDays." + kind.name(), defaultDays);
        if (level.atLeast(FeedTypes.FillLevel.L2)) {
            days *= w.d("fillLadder.windowMultiplier", 6);
        }
        return LocalDateTime.now().minusMinutes(Math.round(days * 24 * 60));
    }

    static int cap(FeedTypes.SourceKind kind, FeedWeights w, int defaultCap) {
        return w.i("caps." + kind.name(), defaultCap);
    }
}
