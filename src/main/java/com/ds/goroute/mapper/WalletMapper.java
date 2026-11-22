package com.ds.goroute.mapper;

import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletCategorySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import com.ds.goroute.entity.WalletReminderSplitRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Queries for the authenticated user's cross-trip expense view only. */
@Mapper
public interface WalletMapper {
    List<WalletCurrencySummaryRow> selectCurrencySummary(@Param("userId") UUID userId);

    List<WalletCategorySummaryRow> selectCategorySummary(@Param("userId") UUID userId);

    List<WalletDebtRow> selectOwedToMe(@Param("userId") UUID userId, @Param("limit") int limit);

    List<WalletDebtRow> selectIOwe(@Param("userId") UUID userId, @Param("limit") int limit);

    List<WalletExpenseRow> selectExpenses(@Param("userId") UUID userId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    long countExpenses(@Param("userId") UUID userId);

    List<ExpensePaymentReminder> selectRemindersBySenderAndKey(@Param("senderUserId") UUID senderUserId,
                                                               @Param("idempotencyKey") String idempotencyKey);

    List<WalletReminderSplitRow> selectReminderSplits(@Param("userId") UUID userId,
                                                      @Param("splitIds") Collection<UUID> splitIds);

    int claimDailyReminder(@Param("senderUserId") UUID senderUserId,
                           @Param("recipientUserId") UUID recipientUserId,
                           @Param("reminderDate") LocalDate reminderDate,
                           @Param("dailyMax") int dailyMax);

    int insertReminders(@Param("reminders") List<ExpensePaymentReminder> reminders);
}
