package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable delivery ledger for user-requested payment reminders. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpensePaymentReminder {
    private UUID id;
    private UUID senderUserId;
    private UUID recipientUserId;
    private UUID expenseId;
    private UUID splitId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
