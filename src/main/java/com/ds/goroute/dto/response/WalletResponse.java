package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Global expense view. Monetary totals are deliberately partitioned by ISO currency. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private List<WalletCurrencySummaryResponse> currencies;
    private List<WalletCategorySummaryResponse> categoryBreakdown;
    private List<WalletDebtResponse> owedToMe;
    private List<WalletDebtResponse> iOwe;
    private List<WalletExpenseResponse> expenses;
    private int page;
    private int pageSize;
    private long totalItems;
}
