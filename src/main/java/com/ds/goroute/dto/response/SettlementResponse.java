package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One payment between two people (epic 05 §8.4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {
    private UUID id;
    private UUID tripId;
    private WalletPersonResponse from;
    private WalletPersonResponse to;
    private BigDecimal amount;
    private String currency;
    private String method;
    private String note;
    private LocalDateTime paidAt;
    private BigDecimal originalAmount;
    private String originalCurrency;
    private WalletPersonResponse createdBy;
    private LocalDateTime createdAt;
    private Boolean deleted;
}
