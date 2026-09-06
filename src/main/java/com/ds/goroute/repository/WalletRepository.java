package com.ds.goroute.repository;

import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    List<WalletCurrencySummaryRow> findCurrencySummary(UUID userId);
    List<WalletDebtRow> findOwedToMe(UUID userId, int limit);
    List<WalletDebtRow> findIOwe(UUID userId, int limit);
    List<WalletExpenseRow> findExpenses(UUID userId, int limit, int offset);
    long countExpenses(UUID userId);
    Optional<ExpensePaymentReminder> findReminder(UUID senderUserId, String idempotencyKey);
    boolean claimReminderCooldown(UUID senderUserId, UUID splitId, LocalDateTime sentAt, LocalDateTime cooldownStart);
    void saveReminder(ExpensePaymentReminder reminder);
}
