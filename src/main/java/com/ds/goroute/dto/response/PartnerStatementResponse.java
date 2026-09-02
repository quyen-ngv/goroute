package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A monthly statement. {@code lines} is populated on the detail endpoint only. */
@Data
@Builder
public class PartnerStatementResponse {
    private UUID id;
    private UUID organizationId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String periodLabel;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private Integer bookingCount;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime settledAt;
    private String note;
    private Integer openDisputes;
    /** Last day a line of this statement can still be disputed; null while it is not issued. */
    private LocalDateTime disputeWindowEndsAt;
    private Long dataVersion;
    private List<PartnerStatementLineResponse> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
