package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStatsResponse {
    private Integer totalItems;
    private Integer checkedInItems;
    private BigDecimal totalExpenses;
    private BigDecimal remainingBudget;
}
