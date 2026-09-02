package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One calendar month of billable activity for one organization. Persistence record, not a DTO. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerStatement {
    private UUID id;
    private UUID organizationId;
    private String organizationName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private Integer bookingCount;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime settledAt;
    private String note;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
