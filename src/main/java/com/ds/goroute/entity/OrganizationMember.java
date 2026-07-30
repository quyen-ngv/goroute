package com.ds.goroute.entity;

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
public class OrganizationMember {
    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String roleCode;
    private String memberStatus;
    private String permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private UUID invitedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
