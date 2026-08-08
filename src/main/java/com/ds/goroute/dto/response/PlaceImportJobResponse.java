package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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
    private String pythonJobId;
    private String currentRegionCode;
    private String currentRegionName;
    private Integer processedCount;
    private Integer eligibleCount;
    private Integer importedCount;
    private Integer rejectedScoreCount;
    private Integer insufficientPhotoCount;
    private Boolean cancelRequested;
    private Integer selectedReviews;
    private Integer minReviewCount;
    private BigDecimal minGoogleRating;
    private BigDecimal minAdjustedRating;
    private String queryMode;
    private List<String> customQueries;
    private List<String> regionCodes;
    private Boolean includeRegionalSpecialties;
    private Boolean includeTouristAreas;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal radiusKm;
    private Integer searchZoom;
    private UUID refreshPlaceId;
    private Integer maxPlaces;
    private Boolean headless;
    private Boolean continueOnError;
    private String currentPlaceId;
    private String currentPlaceTitle;
    private String errorMessage;
    private List<PlaceImportJobItemResponse> items;
    private List<PlaceImportJobRegionResponse> regions;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
