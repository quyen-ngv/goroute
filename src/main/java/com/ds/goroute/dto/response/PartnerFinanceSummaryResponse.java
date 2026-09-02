package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Live accrual for the month in progress plus the last issued statement, so the console can show
 * "what you owe so far" without waiting for the monthly job. Nothing here is persisted.
 */
@Data
@Builder
public class PartnerFinanceSummaryResponse {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private Integer bookingCount;
    private Integer openDisputes;
    private BigDecimal commissionPercent;
    private PartnerStatementResponse lastStatement;
}
