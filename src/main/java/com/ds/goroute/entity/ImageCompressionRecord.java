package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row of {@code image_compressions}: a storage key already handled by compression. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCompressionRecord {
    private String objectKey;
    private String groupCode;
    private String source;
    private String outcome;
    private String tier;
    private int quality;
    private int maxLongEdge;
    private long originalBytes;
    private long compressedBytes;
    private LocalDateTime compressedAt;
}
