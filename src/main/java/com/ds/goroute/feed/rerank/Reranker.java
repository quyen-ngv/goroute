package com.ds.goroute.feed.rerank;

import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy re-rank (design §3.4). Position by position, among the best remaining candidates by
 * score, it places the one with the highest score after every {@link RerankRule}. When the
 * rules forbid all of them it places the best one anyway: a rule shapes the list, it never
 * empties it.
 *
 * <p>Every tenth slot from the fifth is an exploration slot (plan §4.4): it goes to the best
 * post that is new and still little seen, wherever it ranks, so new posts and new authors get
 * the impressions their rates are later measured on.
 */
@Component
@RequiredArgsConstructor
public class Reranker {

    private final List<RerankRule> rules;

    public List<FeedCandidate> rerank(List<FeedCandidate> scored, int size, ViewerContext context,
                                      FeedFilterSpec spec, FeedWeights w, LocalDateTime now) {
        List<FeedCandidate> remaining = new ArrayList<>(scored);
        remaining.sort(Comparator.comparingDouble((FeedCandidate candidate) -> candidate.score()).reversed());
        RerankRule.State state = new RerankRule.State(context, spec, w);
        int lookahead = Math.max(10, w.i("rerank.lookahead", 60));
        int explorationSlot = w.i("rerank.explorationSlot", 4);
        int target = Math.min(size, remaining.size());

        while (state.position() < target) {
            FeedCandidate pick = null;
            if (explorationSlot >= 0 && state.position() % RerankRule.State.WINDOW == explorationSlot) {
                pick = best(remaining.stream().filter(c -> explorable(c, w, now)).toList(),
                        Integer.MAX_VALUE, state);
            }
            if (pick == null) {
                pick = best(remaining, lookahead, state);
            }
            if (pick == null) {
                pick = remaining.get(0);
            }
            remaining.remove(pick);
            state.place(pick);
        }
        return new ArrayList<>(stateList(state));
    }

    private FeedCandidate best(List<FeedCandidate> pool, int lookahead, RerankRule.State state) {
        FeedCandidate best = null;
        double bestScore = 0;
        int looked = 0;
        for (FeedCandidate candidate : pool) {
            if (looked++ >= lookahead) {
                break;
            }
            double adjusted = candidate.score();
            for (RerankRule rule : rules) {
                adjusted *= rule.adjust(candidate, state);
                if (adjusted <= 0) {
                    break;
                }
            }
            if (adjusted > bestScore) {
                bestScore = adjusted;
                best = candidate;
            }
        }
        return best;
    }

    /** New (three days) and seen by fewer than the fresh ceiling of people. */
    private static boolean explorable(FeedCandidate candidate, FeedWeights w, LocalDateTime now) {
        return candidate.row().getImpressionCount() < w.i("fillLadder.freshMaxImpressions", 30)
                && Duration.between(candidate.row().getPublishedAt(), now).toHours() < 72
                && candidate.seen() == null;
    }

    private static List<FeedCandidate> stateList(RerankRule.State state) {
        List<FeedCandidate> placed = new ArrayList<>(state.position());
        for (int back = state.position(); back >= 1; back--) {
            placed.add(state.last(back));
        }
        return placed;
    }
}
