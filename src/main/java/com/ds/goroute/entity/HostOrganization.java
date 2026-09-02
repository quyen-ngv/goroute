package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    /** Platform commission rate in force for new bookings; frozen onto each booking at creation. */
    private BigDecimal commissionPercent;
    private String billingEmail;
    private String billingDetails;
    private LocalDateTime verificationSubmittedAt;
    private String verificationReason;
    private LocalDateTime verificationDecidedAt;
    private UUID verificationDecidedBy;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
