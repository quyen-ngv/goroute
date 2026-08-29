package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A stamp somebody actually earned. Never removed because a rule later changed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportStamp {
    private UUID id;
    private UUID userId;
    private String ruleCode;
    private Integer ruleVersion;
    private UUID triggeringEventId;
    private LocalDateTime awardedAt;
}
