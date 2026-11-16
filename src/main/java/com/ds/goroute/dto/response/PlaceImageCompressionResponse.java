package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImageCompressionOutcome;
import com.ds.goroute.type.PlaceImageCompressionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One place's image compression: the result of a run or dry run, or the stored record. The
 * same shape is what {@code places.image_compression} holds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceImageCompressionResponse {
    private UUID placeId;
    private PlaceImageCompressionStatus status;
    /** Set once recorded. */
    private Instant compressedAt;
    private Integer quality;
    private Integer maxLongEdge;
    private Integer minSavingsPercent;
    /** Distinct image URLs of the place (thumbnail plus images). */
    private int imageCount;
    /** Bytes before and after, over the images COMPRESSED (or WOULD_COMPRESS in a dry run) only. */
    private long originalBytes;
    private long compressedBytes;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** Unchanged by compression: the file is overwritten under the same key. */
        private String url;
        private PlaceImageCompressionOutcome outcome;
        private Long originalBytes;
        private Long compressedBytes;
        /** Why it FAILED. */
        private String message;
    }
}
