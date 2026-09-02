package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One billable booking on a statement, with its own dispute state. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerStatementLine {
    private UUID id;
    private UUID statementId;
    private String bookingType;
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private String bookingCode;
    private String guestName;
    private LocalDate serviceDate;
    private BigDecimal grossAmount;
    private BigDecimal commissionPercent;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private String lineReason;
    private String disputeStatus;
    private String disputeReason;
    private LocalDateTime disputeOpenedAt;
    private LocalDateTime disputeResolvedAt;
    private UUID disputeResolvedBy;
    private String disputeResolutionNote;
    private LocalDateTime createdAt;
}
