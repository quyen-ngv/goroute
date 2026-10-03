package com.ds.goroute.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** An expense split as a payment reminder needs it: who owes, who paid, and the expense. */
@Data
public class WalletReminderSplitRow {
    private UUID splitId;
    private UUID expenseId;
    private UUID tripId;
    private String expenseDescription;
    private BigDecimal amount;
    private String currency;
    private UUID recipientUserId;
    private Boolean isSettled;
    private UUID paidBy;
    private UUID paidByGuestMemberId;
}
