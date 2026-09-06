package com.ds.goroute.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WalletDebtRow {
    private UUID splitId;
    private UUID expenseId;
    private UUID tripId;
    private String tripName;
    private String expenseDescription;
    private BigDecimal amount;
    private String currency;
    private UUID counterpartUserId;
    private UUID counterpartGuestMemberId;
    private String counterpartDisplayName;
    private String counterpartAvatarUrl;
    private LocalDateTime createdAt;
}
