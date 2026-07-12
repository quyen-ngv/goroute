package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceImportJobResponse {
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
    private String errorMessage;
    private List<PlaceImportJobItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
