package com.ds.goroute.entity;

import com.ds.goroute.type.TrustRole;
import com.ds.goroute.type.TrustRoleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A role a person was granted after review (TRUST-02).
 *
 * <p>Deliberately not the same thing as the automatic profile rank. Rank follows from
 * activity and moves on its own; a role is a judgement somebody made, and it expires and
 * gets re-examined. Follower counts can be bought, which is exactly why a number cannot be
 * allowed to grant this.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTrustRole {
    private UUID id;
    private UUID userId;
    private TrustRole role;
    /** Local expertise is expertise somewhere; "expert" on its own means nothing. */
    private String areaProvinceCode;
    private TrustRoleStatus status;
    private String applicationNote;
    private String decisionNote;
    private UUID decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime grantedAt;
    /** Somebody who knew a place well five years ago may not now. */
    private LocalDateTime reviewDueAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
