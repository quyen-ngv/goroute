package com.ds.goroute.feed.score;

import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;

import java.time.LocalDateTime;

/**
 * One multiplicative factor of a post's score for one viewer (design §3.3). The score is the
 * product of every factor bean; each records its value under {@link #name()} for the debug
 * output. A model-based factor later is one more bean.
 */
public interface ScoreFactor {

    String name();

    double apply(FeedCandidate candidate, Request request);

    /** What a factor may look at besides the candidate. */
    record Request(ViewerContext context, FeedFilterSpec spec, FeedWeights weights,
                   FeedTypes.FillLevel level, LocalDateTime now) {
    }
}
