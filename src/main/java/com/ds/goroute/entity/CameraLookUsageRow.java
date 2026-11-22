package com.ds.goroute.entity;

import lombok.Data;

/** How many live check-in photos carry one camera look key. */
@Data
public class CameraLookUsageRow {
    private String lookKey;
    private long usageCount;
}
