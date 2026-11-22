package com.ds.goroute.feed.backend;

import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The backend the ranking uses: FEED.CANDIDATES_BACKEND picks SQL or Lucene. Lucene answers
 * only plans it {@link LuceneCandidateBackend#supports supports} once its index is built; any
 * other plan, and any Lucene failure, goes to SQL, so switching it on can never empty a feed.
 */
@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class RoutingCandidateBackend implements CandidateBackend {

    public enum Mode {
        SQL,
        LUCENE
    }

    private final SqlCandidateBackend sql;
    private final LuceneCandidateBackend lucene;
    private final BusinessConfigService config;

    @Override
    public String name() {
        return "routing";
    }

    @Override
    public List<FeedItemRow> execute(FeedCandidateQuery plan) {
        if (mode() == Mode.LUCENE && lucene.supports(plan)) {
            try {
                return lucene.execute(plan);
            } catch (RuntimeException exception) {
                log.warn("Lucene candidates failed, reading SQL instead: {}", exception.getMessage());
            }
        }
        return sql.execute(plan);
    }

    private Mode mode() {
        try {
            return config.getEnum(BusinessConfigKey.FEED_CANDIDATES_BACKEND, Mode.class);
        } catch (RuntimeException exception) {
            return Mode.SQL;
        }
    }
}
