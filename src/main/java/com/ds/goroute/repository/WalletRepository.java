package com.ds.goroute.repository;

import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletCategorySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import com.ds.goroute.entity.WalletReminderSplitRow;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WalletRepository {
    List<WalletCurrencySummaryRow> findCurrencySummary(UUID userId);
    List<WalletCategorySummaryRow> findCategorySummary(UUID userId);
    List<WalletDebtRow> findOwedToMe(UUID userId, int limit);
    List<WalletDebtRow> findIOwe(UUID userId, int limit);
    List<WalletExpenseRow> findExpenses(UUID userId, int limit, int offset);
    long countExpenses(UUID userId);
    List<ExpensePaymentReminder> findReminders(UUID senderUserId, String idempotencyKey);
    List<WalletReminderSplitRow> findReminderSplits(UUID userId, Collection<UUID> splitIds);
    /** Counts one reminder from sender to recipient on that day, unless the pair already reached dailyMax. */
    boolean claimDailyReminder(UUID senderUserId, UUID recipientUserId, LocalDate day, int dailyMax);
    /** Returns how many rows were written; 0 means a concurrent request with the same key got there first. */
    int saveReminders(List<ExpensePaymentReminder> reminders);
}
