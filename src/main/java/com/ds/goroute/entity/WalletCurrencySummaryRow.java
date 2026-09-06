package com.ds.goroute.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletCurrencySummaryRow {
    private String currency;
    private BigDecimal totalSpent;
    private BigDecimal owedToMe;
    private BigDecimal iOwe;
    private BigDecimal myShare;
    private BigDecimal myPaid;
}
