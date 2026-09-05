package com.ds.goroute.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SocialSavedSpotResponse {
    private UUID id;
    private UUID socialJobId;
    private String candidateRef;
    private String contentType;
    private String name;
    private String query;
    private String description;
    private JsonNode usefulInfo;
    private JsonNode visitGuidance;
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
    private JsonNode evidence;
    private String imageUrl;
    private LocalDateTime createdAt;
}
