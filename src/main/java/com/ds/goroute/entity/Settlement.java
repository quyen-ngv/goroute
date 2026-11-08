package com.ds.goroute.entity;

import com.ds.goroute.type.SettlementMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Money handed from one person to another (epic 05 §2.4f). Either side is a user or a trip
 * guest; outside a trip ({@code tripId} null) both are users. Deleting sets {@code deletedAt}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {
    private UUID id;
    private UUID tripId;
    private UUID fromUserId;
    private UUID fromGuestMemberId;
    private UUID toUserId;
    private UUID toGuestMemberId;
    private BigDecimal amount;
    private String currency;
    private SettlementMethod method;
    private String note;
    private LocalDateTime paidAt;
    /** Paid in another currency: what was handed over; amount/currency is the debt it cleared. */
    private BigDecimal originalAmount;
    private String originalCurrency;
    /** The share an old app build marked as paid, when this settlement stands for it. */
    private UUID splitId;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
