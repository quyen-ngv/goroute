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
public class HostOrganization {
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
    private String settings;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
