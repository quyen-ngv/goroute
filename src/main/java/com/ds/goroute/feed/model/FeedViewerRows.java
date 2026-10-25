package com.ds.goroute.feed.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Small read models of the viewer context queries (FeedViewerMapper). */
public final class FeedViewerRows {

    private FeedViewerRows() {
    }

    /** Someone the viewer follows; mutual when they follow back. */
    @Data
    public static class Followed {
        private UUID userId;
        private boolean mutual;
    }

    @Data
    public static class Affinity {
        private UUID authorId;
        private float score;
    }

    /** A trip of the viewer's that is under way or starts soon. */
    @Data
    public static class ContextTrip {
        private UUID tripId;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    /** One destination of a context trip, with the province its point falls in. */
    @Data
    public static class ContextDestination {
        private UUID tripId;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean primary;
        private String provinceCode;
    }

    /** How often and when the viewer last saw a post. */
    @Data
    public static class Seen {
        private String itemType;
        private UUID itemId;
        private int seenCount;
        private boolean opened;
        private LocalDateTime lastSeenAt;
    }

    /** A post the people the viewer follows interacted with, and how many of them did. */
    @Data
    public static class SocialProof {
        private String itemType;
        private UUID itemId;
        private int people;
    }
}
