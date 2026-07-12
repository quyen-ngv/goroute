package com.ds.goroute.entity;

import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
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
public class PlaceImportJob {
    private UUID id;
    private UUID userId;
    private PlaceImportSourceType sourceType;
    private UUID sourceRefId;
    private PlaceImportJobStatus status;
    private Integer maxReviews;
    private Integer totalItems;
    private Integer skippedExistingCount;
    private Integer triggeredCount;
    private Integer completedCount;
    private Integer failedCount;
    private String requestPayload;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
