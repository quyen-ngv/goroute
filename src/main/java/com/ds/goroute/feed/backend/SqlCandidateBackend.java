package com.ds.goroute.feed.backend;

import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.mapper.FeedItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Candidate plans as one SQL read of feed_items each ({@code FeedItemMapper.selectCandidates}). */
@Component
@RequiredArgsConstructor
public class SqlCandidateBackend implements CandidateBackend {

    private final FeedItemMapper itemMapper;

    @Override
    public String name() {
        return "sql";
    }

    @Override
    public List<FeedItemRow> execute(FeedCandidateQuery plan) {
        return itemMapper.selectCandidates(plan);
    }
}
