package com.ds.goroute.dto.response;

import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** The bulk image compression run of this backend instance (epic 00 §9b). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCompressionRunResponse {
    private ImageCompressionRunStatus status;
    private List<ImageCompressionGroup> groups;
    private ImageCompressionGroup currentGroup;
    private int finishedGroups;
    private Instant startedAt;
    private Instant finishedAt;
    /** Settings the run started with; a config edit applies to the next run. */
    private Integer freeQuality;
    private Integer freeMaxLongEdge;
    private Integer proQuality;
    private Integer proMaxLongEdge;
    private Integer minSavingsPercent;
    /** Objects listed under the groups' prefixes, images among them, and images already recorded. */
    private long listedObjects;
    private long images;
    private long alreadyDone;
    private long compressed;
    private long noSavings;
    private long notImages;
    private long missing;
    private long failed;
    /** Bytes before and after over the COMPRESSED images only. */
    private long originalBytes;
    private long compressedBytes;
    /** Why the run STOPPED. */
    private String message;
    /** Latest failures, newest last, at most 20. */
    private List<String> recentErrors;
}
