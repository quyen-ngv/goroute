package com.ds.goroute.dto.response;

import com.ds.goroute.type.ImageCompressionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An image of a group not compressed yet, for the console to preview. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCompressionSampleResponse {
    private String key;
    private String url;
    private long bytes;
    private ImageCompressionTier tier;
}
