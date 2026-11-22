package com.ds.goroute.feed.rerank;

import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A rule of the greedy re-rank (design §3.4): given what is already placed, how much a
 * candidate's score is worth at the next position. 1 leaves it, 0 forbids it there. Rules see
 * only the last ten placed posts, so a rule shapes each screenful, not the whole list.
 */
public interface RerankRule {

    double adjust(FeedCandidate candidate, State state);

    /** What the rules can see: the posts placed so far and the request. */
    final class State {
        public static final int WINDOW = 10;

        private final List<FeedCandidate> placed = new ArrayList<>();
        private final ViewerContext context;
        private final FeedFilterSpec spec;
        private final FeedWeights weights;

        public State(ViewerContext context, FeedFilterSpec spec, FeedWeights weights) {
            this.context = context;
            this.spec = spec;
            this.weights = weights;
        }

        public int position() {
            return placed.size();
        }

        /** The last {@link #WINDOW} placed posts, oldest first. */
        public List<FeedCandidate> window() {
            return placed.subList(Math.max(0, placed.size() - WINDOW), placed.size());
        }

        public FeedCandidate last(int back) {
            int index = placed.size() - back;
            return index >= 0 ? placed.get(index) : null;
        }

        void place(FeedCandidate candidate) {
            placed.add(candidate);
        }

        public ViewerContext context() {
            return context;
        }

        public FeedFilterSpec spec() {
            return spec;
        }

        public FeedWeights weights() {
            return weights;
        }

        long countInWindow(java.util.function.Predicate<FeedCandidate> test) {
            return window().stream().filter(test).count();
        }
    }
}
