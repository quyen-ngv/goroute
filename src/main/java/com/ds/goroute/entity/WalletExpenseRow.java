package com.ds.goroute.entity;

import com.ds.goroute.type.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Flat mapper projection. WalletService groups split rows into public DTOs. */
@Data
public class WalletExpenseRow {
    private UUID expenseId;
    private UUID tripId;
    private String tripName;
    private BigDecimal amount;
    private String currency;
    private ExpenseCategory category;
    private String description;
    private UUID activityId;
    private UUID paidByUserId;
    private UUID paidByGuestMemberId;
    private String paidByGuestName;
    private String paidByDisplayName;
    private String paidByAvatarUrl;
    private LocalDateTime createdAt;
    private UUID splitId;
    private UUID splitUserId;
    private UUID splitGuestMemberId;
    private String splitGuestName;
    private String splitDisplayName;
    private String splitAvatarUrl;
    private BigDecimal splitAmount;
    private Boolean splitSettled;
    private LocalDateTime splitSettledAt;
}
