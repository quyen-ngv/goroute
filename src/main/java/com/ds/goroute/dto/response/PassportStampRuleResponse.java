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
    private String conditionType;
    private int threshold;
    private String icon;
    private int rewardPoints;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
}
