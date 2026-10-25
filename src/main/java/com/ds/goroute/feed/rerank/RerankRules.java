package com.ds.goroute.feed.rerank;

import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** The rules of plan §4.4, §4.8, §4.9 and §4.11. Each is a bean; order does not matter. */
public final class RerankRules {

    private RerankRules() {
    }

    /**
     * One author does not fill a screen: each earlier post of theirs in the last ten halves
     * the next one, and a third is not placed. Kept even when the app has few authors — loosen
     * it and the feed turns into the profile of its five most active people.
     */
    @Component
    public static class Author implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            FeedWeights w = state.weights();
            long same = state.countInWindow(placed -> Objects.equals(
                    placed.row().getAuthorId(), candidate.row().getAuthorId()));
            if (same >= w.i("rerank.authorMaxPer10", 2)) {
                return 0;
            }
            return Math.pow(w.d("rerank.authorDecay", 0.5), same);
        }
    }

    /** Not three posts of one kind in a row (three trips, three reviews). */
    @Component
    public static class TypeRun implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            if (state.spec().singleType()) {
                return 1;
            }
            FeedCandidate previous = state.last(1);
            FeedCandidate beforeThat = state.last(2);
            if (previous != null && beforeThat != null
                    && previous.row().getItemType().equals(candidate.row().getItemType())
                    && beforeThat.row().getItemType().equals(candidate.row().getItemType())) {
                return state.weights().d("rerank.typeRunPenalty", 0.7);
            }
            return 1;
        }
    }

    /** Two posts in a row from one province are fine; the next one costs a little. */
    @Component
    public static class Province implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            FeedCandidate previous = state.last(1);
            String province = candidate.row().getProvinceCode();
            if (previous != null && province != null && province.equals(previous.row().getProvinceCode())) {
                return state.weights().d("rerank.sameProvincePenalty", 0.85);
            }
            return 1;
        }
    }

    /**
     * A trip without memory photos shows the city's stock picture: at most one per ten posts,
     * never in the first three — unless it is on the viewer's own route or where they are
     * today, when the itinerary is the very thing they need.
     */
    @Component
    public static class PhotolessTrip implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            if (!candidate.photolessTrip() || state.spec().tripsChip()) {
                return 1;
            }
            FeedWeights w = state.weights();
            boolean onRoute = candidate.contextBoost() >= 0.8;
            if (!onRoute && state.position() < w.i("rerank.photolessTripNoTop", 3)) {
                return 0;
            }
            if (state.countInWindow(FeedCandidate::photolessTrip) >= w.i("rerank.photolessTripPer10", 1)) {
                return 0;
            }
            return 1;
        }
    }

    /**
     * On a trip or with GPS on, at most four posts in ten come from the context, so the feed
     * stays a feed and does not turn into "around here" (Explore already is that).
     */
    @Component
    public static class ContextCap implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            if (!state.context().anyContext() || candidate.contextBoost() < 0.6) {
                return 1;
            }
            long inContext = state.countInWindow(placed -> placed.contextBoost() >= 0.6);
            return inContext >= state.weights().i("rerank.contextPer10", 4) ? 0 : 1;
        }
    }

    /** Under a place chip: not more than two posts about one place in ten, and two trips. */
    @Component
    public static class PlaceChip implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            if (!state.spec().hasPlaceKind()) {
                return 1;
            }
            FeedWeights w = state.weights();
            if (candidate.row().isTrip()) {
                long trips = state.countInWindow(placed -> placed.row().isTrip());
                return trips >= w.i("chipTrips.per10", 2) ? 0 : 1;
            }
            if (candidate.row().getPlaceId() == null) {
                return 1;
            }
            long samePlace = state.countInWindow(placed ->
                    candidate.row().getPlaceId().equals(placed.row().getPlaceId()));
            return samePlace >= w.i("rerank.samePlacePer10", 2) ? 0 : 1;
        }
    }

    /**
     * For someone who follows a few people, their posts are at most half of each ten: the
     * Friends tab is where all of them are.
     */
    @Component
    public static class InNetworkShare implements RerankRule {
        @Override
        public double adjust(FeedCandidate candidate, State state) {
            FeedWeights w = state.weights();
            if (state.context().getFollowing().size() < w.i("rerank.inNetworkMinFollows", 3)
                    || !candidate.from(FeedTypes.SourceKind.IN_NETWORK)) {
                return 1;
            }
            long inNetwork = state.countInWindow(placed -> placed.from(FeedTypes.SourceKind.IN_NETWORK));
            return inNetwork >= Math.round(w.d("rerank.inNetworkMaxRatio", 0.5) * State.WINDOW) ? 0.3 : 1;
        }
    }
}
