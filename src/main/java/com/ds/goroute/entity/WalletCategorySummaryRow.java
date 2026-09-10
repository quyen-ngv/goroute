package com.ds.goroute.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletCategorySummaryRow {
    private String currency;
    private String category;
    private BigDecimal amount;
}
