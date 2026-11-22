package com.ds.goroute.feed;

/** Small enums shared by the ranking pipeline. */
public final class FeedTypes {

    private FeedTypes() {
    }

    /** Where a candidate came from; a post can come from several. */
    public enum SourceKind {
        IN_NETWORK,
        SOCIAL,
        GEO,
        ITINERARY,
        NEAR_ME,
        TRENDING,
        FRESH
    }

    /**
     * How far the ranking had to widen to find enough posts (plan §6.1). Each level keeps
     * everything the one before relaxed.
     */
    public enum FillLevel {
        /** Standard windows; posts seen twice in three days left out. */
        L1,
        /** Windows six times longer. */
        L2,
        /** Seen posts come back, pushed far down. */
        L3,
        /** No windows at all; the trip gate relaxed. */
        L4,
        /** Still short: everything there is, and the app says the viewer has seen it all. */
        L5;

        public boolean atLeast(FillLevel other) {
            return compareTo(other) >= 0;
        }
    }
}
