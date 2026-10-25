package com.ds.goroute.feed.model;

import com.ds.goroute.type.FeedItemType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One Feed post by kind and id: the key of feed_items, feed_index_queue and
 * feed_impressions. A mutable bean rather than a record because MyBatis reads and fills it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedKey {

    /** CHECKIN, REVIEW or TRIP. */
    private String itemType;
    private UUID itemId;

    /** Only when read from feed_index_queue: when the post was last queued. */
    private LocalDateTime queuedAt;

    public FeedKey(String itemType, UUID itemId) {
        this(itemType, itemId, null);
    }

    public static FeedKey of(FeedItemType type, UUID id) {
        return new FeedKey(type.name(), id, null);
    }

    public FeedItemType type() {
        return FeedItemType.valueOf(itemType);
    }

    /** "TYPE:id", for maps and sets. */
    public String key() {
        return itemType + ':' + itemId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FeedKey that
                && java.util.Objects.equals(itemType, that.itemType)
                && java.util.Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(itemType, itemId);
    }
}
