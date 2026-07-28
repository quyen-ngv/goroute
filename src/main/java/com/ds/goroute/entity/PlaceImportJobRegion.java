package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceImportJobRegion {
    private UUID id;
    private UUID jobId;
    private String regionCode;
    private String regionName;
    private Integer priority;
    private Integer sequenceNo;
    private String status;
    private Integer queryCount;
    private Integer discoveredCount;
    private Integer processedCount;
    private Integer eligibleCount;
    private Integer importedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
