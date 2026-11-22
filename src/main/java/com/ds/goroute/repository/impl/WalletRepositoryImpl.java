package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletCategorySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import com.ds.goroute.entity.WalletReminderSplitRow;
import com.ds.goroute.mapper.WalletMapper;
import com.ds.goroute.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {
    private final WalletMapper walletMapper;

    @Override public List<WalletCurrencySummaryRow> findCurrencySummary(UUID userId) { return walletMapper.selectCurrencySummary(userId); }
    @Override public List<WalletCategorySummaryRow> findCategorySummary(UUID userId) { return walletMapper.selectCategorySummary(userId); }
    @Override public List<WalletDebtRow> findOwedToMe(UUID userId, int limit) { return walletMapper.selectOwedToMe(userId, limit); }
    @Override public List<WalletDebtRow> findIOwe(UUID userId, int limit) { return walletMapper.selectIOwe(userId, limit); }
    @Override public List<WalletExpenseRow> findExpenses(UUID userId, int limit, int offset) { return walletMapper.selectExpenses(userId, limit, offset); }
    @Override public long countExpenses(UUID userId) { return walletMapper.countExpenses(userId); }
    @Override public List<ExpensePaymentReminder> findReminders(UUID senderUserId, String idempotencyKey) { return walletMapper.selectRemindersBySenderAndKey(senderUserId, idempotencyKey); }
    @Override public List<WalletReminderSplitRow> findReminderSplits(UUID userId, Collection<UUID> splitIds) { return walletMapper.selectReminderSplits(userId, splitIds); }
    @Override public boolean claimDailyReminder(UUID senderUserId, UUID recipientUserId, LocalDate day, int dailyMax) { return walletMapper.claimDailyReminder(senderUserId, recipientUserId, day, dailyMax) == 1; }
    @Override public int saveReminders(List<ExpensePaymentReminder> reminders) { return walletMapper.insertReminders(reminders); }
}
