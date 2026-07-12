package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImportApprovalStatus;
import com.ds.goroute.type.PlaceImportJobItemStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlaceImportMappingResponse {
    private UUID id;
    private UUID jobId;
    private PlaceImportSourceType sourceType;
    private UUID sourceRefId;
    private UUID activityId;
    private String activityName;
    private String candidateName;
    private String sourceUrl;
    private String sourceAddress;
    private String sourceOriginalUrl;
    private String googlePlaceId;
    private String cid;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private UUID mappedPlaceId;
    private String mappedPlaceName;
    private String mappedPlaceAddress;
    private String mappedPlaceGoogleMapsLink;
    private String mappedPlaceVisibilityStatus;
    private PlaceImportJobItemStatus status;
    private PlaceImportApprovalStatus approvalStatus;
    private String approvalNote;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}
