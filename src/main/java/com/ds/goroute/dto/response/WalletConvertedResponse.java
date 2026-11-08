package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The Wallet's per-currency totals added up in one currency at today's rates. An estimate for
 * reading only: the native rows in {@link WalletResponse} stay the amounts people actually owe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletConvertedResponse {
    private String currency;
    private BigDecimal totalSpent;
    private BigDecimal owedToMe;
    private BigDecimal iOwe;
    private BigDecimal myShare;
    private BigDecimal myPaid;
    /** Every row's {@code currency} is {@link #currency}. */
    private List<WalletCategorySummaryResponse> categoryBreakdown;
    /** Rate used per source currency: 1 unit of the key = value in {@link #currency}. */
    private Map<String, BigDecimal> rates;
    /** Currencies with no rate today; their amounts are left out of every total above. */
    private List<String> unconvertedCurrencies;
}
