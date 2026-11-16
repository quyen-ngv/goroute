package com.ds.goroute.entity;

import lombok.Data;

/** {@code image_compressions} totals of one group and outcome. */
@Data
public class ImageCompressionGroupTotal {
    private String groupCode;
    private String outcome;
    private long imageCount;
    private long originalBytes;
    private long compressedBytes;
}
