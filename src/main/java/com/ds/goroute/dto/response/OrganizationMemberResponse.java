package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrganizationMemberResponse {
    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String roleCode;
    private String memberStatus;
    private List<String> permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
