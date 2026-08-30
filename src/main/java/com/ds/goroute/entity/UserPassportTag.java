package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable record of a traveller having earned a configured passport tag. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPassportTag {
    private UUID userId;
    private UUID passportTagId;
    private UUID triggeringEventId;
    private LocalDateTime awardedAt;
}
