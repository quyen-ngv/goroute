package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Console place image compression. A null option falls back to the {@code caesium.*} default. */
@Data
public class CompressPlaceImagesRequest {

    /** WebP quality. */
    @Min(30)
    @Max(95)
    private Integer quality;

    /** Longest side in pixels; never scales up. */
    @Min(640)
    @Max(4096)
    private Integer maxLongEdge;

    /** An image is overwritten only when the WebP is at least this much smaller (default 10). */
    @Min(0)
    @Max(90)
    private Integer minSavingsPercent;

    /** Compress and report sizes without writing anything. Ignored by the bulk run. */
    private boolean dryRun;
}
