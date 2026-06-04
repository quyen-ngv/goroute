package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetOverviewResponse {
    private BigDecimal totalBudget;
    private BigDecimal totalEstimated;
    private BigDecimal totalSpent;
    private BigDecimal remaining;
    private Integer percentageSpent;
    private String currency;
    private BigDecimal myShare;
    private BigDecimal myPaid;
    private Map<String, BigDecimal> byCategory;
}
