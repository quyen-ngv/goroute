package com.ds.goroute.mapper;

import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletCategorySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    ExpensePaymentReminder selectReminderBySenderAndKey(@Param("senderUserId") UUID senderUserId,
                                                         @Param("idempotencyKey") String idempotencyKey);

    int claimReminderCooldown(@Param("senderUserId") UUID senderUserId,
                              @Param("splitId") UUID splitId,
                              @Param("sentAt") LocalDateTime sentAt,
                              @Param("cooldownStart") LocalDateTime cooldownStart);

    int insertReminder(ExpensePaymentReminder reminder);
}
