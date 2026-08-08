package com.ds.goroute.entity;

import com.ds.goroute.type.SocialLocationRestrictionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLocationUserRestriction {
    private UUID userId;
    private Integer strikeCount;
    private SocialLocationRestrictionStatus status;
    private LocalDateTime blockedUntil;
    private String reasonCode;
    private String reasonMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
