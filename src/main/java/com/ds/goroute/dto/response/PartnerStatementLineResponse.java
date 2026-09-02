package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PartnerStatementLineResponse {
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
    private String disputeResolutionNote;
    /** False once the 14-day window closed or the line already carries a dispute. */
    private Boolean disputable;
    private LocalDateTime createdAt;
}
