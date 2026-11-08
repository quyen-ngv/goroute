package com.ds.goroute.feed.model;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * One row of feed_events (V267). A value class with getters because the mapper XML reads it
 * through OGNL.
 */
@Value
@Builder
public class FeedEventRow {

    UUID userId;
    /** CHECKIN, REVIEW or TRIP. */
    String itemType;
    UUID itemId;
    /** SERVE, IMPRESSION, OPEN, or what the app sends (GLANCE, LIKE, ...). */
    String event;
    String surface;
    String variant;
    String sessionId;
    Integer position;
    Integer dwellMs;
    /** JSON text of the factor values, SERVE only. */
    String features;
}
