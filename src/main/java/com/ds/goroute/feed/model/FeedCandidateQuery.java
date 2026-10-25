package com.ds.goroute.feed.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One candidate source's read of feed_items. The source sets what it selects by (authors,
 * provinces, places, a point, explicit keys, an engagement or a recency order); the viewer
 * and the request's filters are the same for every source of one request.
 *
 * <p>A value class with getters because the mapper XML reads it through OGNL.
 */
@Value
@Builder(toBuilder = true)
public class FeedCandidateQuery {

    // --- viewer: own posts, blocks either way, hides and joined trips leave every source
    UUID viewerId;

    // --- request filters (FeedFilterSpec)
    List<String> itemTypes;
    List<String> placeGroups;
    List<String> subTypes;
    /** Under a place chip, a trip qualifies with this many stops of the kind... */
    Integer chipTripMinStops;
    /** ...making up at least this share of its stops. */
    Double chipTripMinRatio;
    /** The Trips chip: every eligible trip, without the recommendation gate. */
    boolean ignoreRecommendGate;
    /** Fill ladder L4: the gate relaxed to "a photo or two stops". */
    boolean relaxedRecommendGate;
    /** The "near me" chip: a hard radius. */
    Double radiusLat;
    Double radiusLng;
    Double radiusMeters;

    // --- what the source selects by (at most one is set per source)
    List<UUID> authorIds;
    List<String> provinceCodes;
    List<UUID> placeIds;
    Double nearLat;
    Double nearLng;
    Double nearMeters;
    List<FeedKey> keys;

    // --- window and order
    LocalDateTime since;
    Integer maxImpressions;
    boolean orderByEngagement;
    int limit;
}
