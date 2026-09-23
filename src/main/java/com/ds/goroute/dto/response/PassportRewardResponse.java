package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PassportRewardResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    /** Raw English copy for the operator catalogue; null on traveller-facing reads, which are already localized. */
    private String nameEn;
    private String descriptionEn;
    private int pointsCost;
    private String requiredStampCode;
    private Integer totalQuantity;
    private int issuedQuantity;
    private int validDays;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
