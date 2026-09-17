package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An account the operator has vouched for as a VietdeGuide.
 *
 * <p>The row survives revocation and carries a status instead of being deleted: "was a guide until
 * March" and "never was one" are different facts, and support needs to tell them apart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGuideGrant {
    private UUID userId;

    /** {@code ACTIVE} or {@code REVOKED}. */
    private String status;

    /** What the badge says, when the guide has a speciality worth naming. */
    private String displayTitle;
    private String note;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
    private UUID revokedBy;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Tourist areas this guide covers; loaded alongside the grant, never part of the row. */
    private List<UUID> locationImageIds;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
