package com.ds.goroute.entity;

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
public class SocialSavedSpot {
    private UUID id;
    private UUID userId;
    private UUID socialJobId;
    private String candidateRef;
    private String contentType;
    private String name;
    private String query;
    private String description;
    private String usefulInfo;
    private String visitGuidance;
    private String addressHint;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String googlePlaceId;
    private UUID placeId;
    private Integer dayHint;
    private Integer sequence;
    private String timeHint;
    private String optionGroupId;
    private Integer optionIndex;
    private String relation;
    private String identityStatus;
    private String resolutionStatus;
    private String evidence;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
