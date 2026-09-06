package com.ds.goroute.dto.response;

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
public class WalletExpenseSplitResponse {
    private UUID id;
    private WalletPersonResponse person;
    private BigDecimal amount;
    private Boolean isSettled;
    private LocalDateTime settledAt;
}
