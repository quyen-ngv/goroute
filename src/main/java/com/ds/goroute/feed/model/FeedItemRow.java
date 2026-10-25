package com.ds.goroute.feed.model;

import com.ds.goroute.type.FeedItemType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of feed_items (V249): everything the ranked feed filters and scores a post on. */
@Data
public class FeedItemRow {
    private String itemType;
    private UUID itemId;
    private UUID authorId;
    private LocalDateTime publishedAt;

    private String provinceCode;
    private UUID placeId;
    private String placeGroup;
    private String[] subTypes;
    private Double latitude;
    private Double longitude;

    private boolean hasPhoto;
    private int photoCount;
    private int textLen;
    private float authorTrust;

    private int stopCount;
    private int linkedStopCount;
    private int dayCount;
    private boolean hasCover;
    private boolean hasCost;
    private LocalDate tripEndDate;
    /** JSON object of stop counts by place group and sub type code; trips only. */
    private String stopKindCounts;

    private int likeCount;
    private int commentCount;
    private int saveCount;
    private int copyCount;
    private int impressionCount;
    private int hideCount;
    private int reportCount;

    private float qualityScore;
    private float engagementScore;
    private boolean eligible;
    private boolean recommendOk;

    public FeedItemType type() {
        return FeedItemType.valueOf(itemType);
    }

    public FeedKey key() {
        return new FeedKey(itemType, itemId);
    }

    public boolean isTrip() {
        return "TRIP".equals(itemType);
    }
}
