package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImportJobItemStatus;
import com.ds.goroute.type.PlaceImportApprovalStatus;
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
public class PlaceImportJobItemResponse {
    private UUID id;
    private UUID sourceRefId;
    private UUID activityId;
    private String sourceUrl;
    private String sourceAddress;
    private String sourceOriginalUrl;
    private String sourceCandidateKey;
    private String candidateName;
    private String googlePlaceId;
    private String cid;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private UUID existingPlaceId;
    private UUID importedPlaceId;
    private String pythonJobId;
    private PlaceImportJobItemStatus status;
    private PlaceImportApprovalStatus approvalStatus;
    private String approvalNote;
    private LocalDateTime approvedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
