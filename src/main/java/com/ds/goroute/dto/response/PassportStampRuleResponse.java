package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PassportStampRuleResponse {
    private String code;
    private int version;
    private String name;
    private String description;
    /** Raw English copy for the operator catalogue; null on traveller-facing reads, which are already localized. */
    private String nameEn;
    private String descriptionEn;
    private String conditionType;
    private int threshold;
    private String icon;
    private int rewardPoints;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
}
