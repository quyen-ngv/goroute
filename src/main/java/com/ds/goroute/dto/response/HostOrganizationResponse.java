package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class HostOrganizationResponse {
    private UUID id;
    private UUID ownerUserId;
    private String legalName;
    private String displayName;
    private String organizationType;
    private String verificationStatus;
    private String operationalStatus;
    private String defaultCurrency;
    private String timezone;
    private String contactEmail;
    private String contactPhone;
    private Map<String, Object> settings;
    private Long dataVersion;
    private String currentUserRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
