package com.ds.goroute.feed.source;

import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedTypes.FillLevel;
import com.ds.goroute.feed.FeedTypes.SourceKind;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedKey;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The seven candidate sources of plan §4.1. Each is a bean; the ranking service runs every
 * one that applies, in parallel.
 */
public final class FeedSources {

    private FeedSources() {
    }

    /** Posts by the people the viewer follows, closest first in the author list. */
    @Component
    public static class InNetwork implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.IN_NETWORK;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return !context.getFollowing().isEmpty();
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            return base.toBuilder()
                    .authorIds(new ArrayList<>(context.getFollowing().keySet()))
                    .since(CandidateSource.since(kind(), w, level, 30))
                    .limit(CandidateSource.cap(kind(), w, 300))
                    .build();
        }
    }

    /** Posts the people the viewer follows liked, commented on or saved lately. */
    @Component
    public static class Social implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.SOCIAL;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return !context.getSocialProof().isEmpty();
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            int cap = CandidateSource.cap(kind(), w, 150);
            List<FeedKey> keys = context.getSocialProof().keySet().stream()
                    .limit(cap)
                    .map(Social::parse)
                    .toList();
            return base.toBuilder().keys(keys).limit(cap).build();
        }

        static FeedKey parse(String key) {
            int colon = key.indexOf(':');
            return new FeedKey(key.substring(0, colon), UUID.fromString(key.substring(colon + 1)));
        }
    }

    /**
     * Posts in the provinces the viewer is tied to: where they are today, where they go
     * next, their Passport wishes, where they checked in lately, where the phone is.
     */
    @Component
    public static class Geo implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.GEO;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return !provinces(context).isEmpty();
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            return base.toBuilder()
                    .provinceCodes(new ArrayList<>(provinces(context)))
                    .since(CandidateSource.since(kind(), w, level, 30))
                    .limit(CandidateSource.cap(kind(), w, 300))
                    .build();
        }

        private static Set<String> provinces(ViewerContext context) {
            Set<String> provinces = new HashSet<>(context.getRelatedProvinces());
            if (!context.isContextOff()) {
                provinces.addAll(context.getTodayProvinces());
                provinces.addAll(context.getUpcomingProvinces());
            }
            return provinces;
        }
    }

    /** Posts about the very places on the viewer's ongoing and upcoming itineraries. */
    @Component
    public static class Itinerary implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.ITINERARY;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return !context.isContextOff() && !context.getItineraryPlaceIds().isEmpty();
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            return base.toBuilder()
                    .placeIds(new ArrayList<>(context.getItineraryPlaceIds()))
                    .since(CandidateSource.since(kind(), w, level, 365))
                    .limit(CandidateSource.cap(kind(), w, 150))
                    .build();
        }
    }

    /** Posts around where the phone is now. A post about the café next door is worth months. */
    @Component
    public static class NearMe implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.NEAR_ME;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return context.hasDevice();
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            return base.toBuilder()
                    .nearLat(context.getDeviceLat())
                    .nearLng(context.getDeviceLng())
                    .nearMeters(w.d("context.nearDeviceKm", 30) * 1000)
                    .since(CandidateSource.since(kind(), w, level, 180))
                    .limit(CandidateSource.cap(kind(), w, 200))
                    .build();
        }
    }

    /** The best posts of the last week, by their stored E x Q x N. Everyone gets these. */
    @Component
    public static class Trending implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.TRENDING;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return true;
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            return base.toBuilder()
                    .since(CandidateSource.since(kind(), w, level, 7))
                    .orderByEngagement(true)
                    .limit(CandidateSource.cap(kind(), w, 200))
                    .build();
        }
    }

    /**
     * New posts that few people have seen yet, for the exploration slots. From L4 the window
     * and the impression ceiling go and the cap rises to the whole candidate budget: with few
     * posts in the app, this is what makes every post reachable.
     */
    @Component
    public static class Fresh implements CandidateSource {
        @Override
        public SourceKind kind() {
            return SourceKind.FRESH;
        }

        @Override
        public boolean applies(ViewerContext context, FeedFilterSpec spec) {
            return true;
        }

        @Override
        public FeedCandidateQuery query(FeedCandidateQuery base, ViewerContext context, FeedWeights w, FillLevel level) {
            if (level.atLeast(FillLevel.L4)) {
                return base.toBuilder()
                        .limit(w.i("caps.maxCandidates", 1400))
                        .build();
            }
            return base.toBuilder()
                    .since(CandidateSource.since(kind(), w, level, 1))
                    .maxImpressions(w.i("fillLadder.freshMaxImpressions", 30))
                    .limit(CandidateSource.cap(kind(), w, 100))
                    .build();
        }
    }
}
