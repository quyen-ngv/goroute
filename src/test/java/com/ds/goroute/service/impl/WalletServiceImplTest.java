package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.WalletResponse;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.WalletRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceImplTest {
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final ExpenseSplitRepository splits = mock(ExpenseSplitRepository.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final NotificationHelper notifications = mock(NotificationHelper.class);
    private WalletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(walletRepository, expenses, splits, members, accessGuard, config, notifications);
    }

    @Test
    void aggregatesNativeCurrenciesWithoutCombiningThemAndScopesEveryQueryToCaller() {
        UUID userId = UUID.randomUUID();
        WalletCurrencySummaryRow vnd = currency("VND", "250000", "125000", "30000", "70000", "40000");
        WalletCurrencySummaryRow usd = currency("USD", "40.50", "12.50", "0", "18", "18");
        when(walletRepository.findCurrencySummary(userId)).thenReturn(List.of(vnd, usd));
        when(walletRepository.findOwedToMe(userId, 100)).thenReturn(List.of());
        when(walletRepository.findIOwe(userId, 100)).thenReturn(List.of());
        when(walletRepository.findExpenses(userId, 30, 0)).thenReturn(List.of());
        when(walletRepository.countExpenses(userId)).thenReturn(0L);

        WalletResponse response = service.getWallet(userId, 0, 30, 100);

        assertThat(response.getCurrencies()).hasSize(2);
        assertThat(response.getCurrencies().get(0).getCurrency()).isEqualTo("VND");
        assertThat(response.getCurrencies().get(0).getTotalSpent()).isEqualByComparingTo("250000");
        assertThat(response.getCurrencies().get(0).getOwedToMe()).isEqualByComparingTo("125000");
        assertThat(response.getCurrencies().get(1).getCurrency()).isEqualTo("USD");
        assertThat(response.getCurrencies().get(1).getMyPaid()).isEqualByComparingTo("18");
        verify(walletRepository).findExpenses(userId, 30, 0);
        verify(walletRepository).countExpenses(userId);
    }

    @Test
    void rejectsReminderWhenCallerIsNotTheRegisteredPayer() {
        UUID userId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(walletRepository.findReminder(userId, "request-1")).thenReturn(Optional.empty());
        when(expenses.findById(expenseId)).thenReturn(Optional.of(Expense.builder()
                .id(expenseId).tripId(tripId).paidBy(UUID.randomUUID()).build()));
        when(accessGuard.requireAccess(tripId, userId)).thenReturn(Trip.builder().id(tripId).ownerId(userId).build());

        assertThatThrownBy(() -> service.sendReminder(expenseId, splitId, "request-1", userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("registered payer");
        verify(splits, never()).findById(any());
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendsOneReminderToOutstandingRegisteredRecipientAndRecordsIt() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(walletRepository.findReminder(payer, "request-2")).thenReturn(Optional.empty());
        when(expenses.findById(expenseId)).thenReturn(Optional.of(Expense.builder()
                .id(expenseId).tripId(tripId).paidBy(payer).currency("USD").description("Dinner").build()));
        when(accessGuard.requireAccess(tripId, payer)).thenReturn(Trip.builder().id(tripId).ownerId(recipient).build());
        when(splits.findById(splitId)).thenReturn(ExpenseSplit.builder()
                .id(splitId).expenseId(expenseId).userId(recipient).amount(new BigDecimal("12.50"))
                .isSettled(false).build());
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_COOLDOWN_MINUTES)).thenReturn(60);
        when(walletRepository.claimReminderCooldown(eq(payer), eq(splitId), any(), any())).thenReturn(true);

        var response = service.sendReminder(expenseId, splitId, "request-2", payer);

        assertThat(response.getExpenseId()).isEqualTo(expenseId);
        assertThat(response.getRecipientUserId()).isEqualTo(recipient);
        verify(walletRepository).saveReminder(any(ExpensePaymentReminder.class));
        verify(notifications).emitGeneric(eq(tripId), eq(payer), eq(NotificationType.PAYMENT_REMINDER),
                any(), eq(List.of(recipient)), eq(null));
    }

    @Test
    void returnsPriorIdempotentReminderWithoutSendingItAgain() {
        UUID sender = UUID.randomUUID();
        ExpensePaymentReminder saved = ExpensePaymentReminder.builder().id(UUID.randomUUID())
                .senderUserId(sender).recipientUserId(UUID.randomUUID()).expenseId(UUID.randomUUID())
                .splitId(UUID.randomUUID()).idempotencyKey("same-key").createdAt(LocalDateTime.now()).build();
        when(walletRepository.findReminder(sender, "same-key")).thenReturn(Optional.of(saved));

        assertThat(service.sendReminder(saved.getExpenseId(), saved.getSplitId(), "same-key", sender).getId())
                .isEqualTo(saved.getId());
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rateLimitsASecondReminderForTheSameSplit() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(walletRepository.findReminder(payer, "request-3")).thenReturn(Optional.empty());
        when(expenses.findById(expenseId)).thenReturn(Optional.of(Expense.builder()
                .id(expenseId).tripId(tripId).paidBy(payer).build()));
        when(accessGuard.requireAccess(tripId, payer)).thenReturn(Trip.builder().id(tripId).ownerId(recipient).build());
        when(splits.findById(splitId)).thenReturn(ExpenseSplit.builder()
                .id(splitId).expenseId(expenseId).userId(recipient).isSettled(false).build());
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_COOLDOWN_MINUTES)).thenReturn(60);
        when(walletRepository.claimReminderCooldown(eq(payer), eq(splitId), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.sendReminder(expenseId, splitId, "request-3", payer))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.EXPENSE_PAYMENT_REMINDER_RATE_LIMITED);
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    private WalletCurrencySummaryRow currency(String code, String totalSpent, String owed, String owe, String share, String paid) {
        WalletCurrencySummaryRow row = new WalletCurrencySummaryRow();
        row.setCurrency(code);
        row.setTotalSpent(new BigDecimal(totalSpent));
        row.setOwedToMe(new BigDecimal(owed));
        row.setIOwe(new BigDecimal(owe));
        row.setMyShare(new BigDecimal(share));
        row.setMyPaid(new BigDecimal(paid));
        return row;
    }
}
