package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Read model used by both the traveller Passport and the operator catalogue. */
@Data
@Builder
public class PassportTagResponse {
    private UUID id;
    private UUID passportId;
    private String passportCode;
    private String passportName;
    private String code;
    private String name;
    private String description;
    /** Raw English copy for the operator catalogue; null on traveller-facing reads, which are already localized. */
    private String nameEn;
    private String descriptionEn;
    private String imageUrl;
    private String qualificationMode;
    private int requiredCheckinCount;
    @JsonProperty("isActive")
    private boolean isActive;
    private int displayOrder;
    private List<UUID> placeIds;
    private LocalDateTime earnedAt;
    private UUID triggeringEventId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
