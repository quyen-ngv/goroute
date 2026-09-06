package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One outstanding split. A guest is intentionally display-only and cannot receive a reminder. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDebtResponse {
    private UUID splitId;
    private UUID expenseId;
    private UUID tripId;
    private String tripName;
    private String expenseDescription;
    private BigDecimal amount;
    private String currency;
    private WalletPersonResponse counterpart;
    private Boolean isSettled;
    private Boolean canRemind;
    private LocalDateTime createdAt;
}
