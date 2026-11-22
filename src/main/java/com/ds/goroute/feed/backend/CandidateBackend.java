package com.ds.goroute.feed.backend;

import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;

import java.util.List;

/**
 * Runs one candidate source's plan (plan §3.2, §6.2). A source only says what it selects by,
 * in which window, order and cap; the backend decides how to read it. Today the only backend is
 * SQL over feed_items; a Lucene backend later implements the same plan.
 */
public interface CandidateBackend {

    String name();

    /** The rows the plan selects, at most {@code plan.limit}, in the plan's order. */
    List<FeedItemRow> execute(FeedCandidateQuery plan);
}
