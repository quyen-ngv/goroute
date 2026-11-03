package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PassportStampResponse {
    private String code;
    private Integer version;
    private String name;
    private String description;
    private String icon;
    /** The rule's reward and target, so the app can show the hardest-won stamps first. */
    private Integer rewardPoints;
    private Integer threshold;
    private LocalDateTime awardedAt;
    /** The event that earned it, so "why do I have this" has an answer. */
    private UUID triggeringEventId;
}
