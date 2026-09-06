package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** All values are native amounts in one currency; callers must never combine rows. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCurrencySummaryResponse {
    private String currency;
    private BigDecimal totalSpent;
    private BigDecimal owedToMe;
    private BigDecimal iOwe;
    private BigDecimal myShare;
    private BigDecimal myPaid;
}
