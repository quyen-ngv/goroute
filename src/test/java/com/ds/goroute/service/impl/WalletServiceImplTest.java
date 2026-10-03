package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.WalletResponse;
import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletCategorySummaryRow;
import com.ds.goroute.entity.WalletReminderSplitRow;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.WalletRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class WalletServiceImplTest {
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final NotificationHelper notifications = mock(NotificationHelper.class);
    private WalletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(walletRepository, members, accessGuard, config, notifications);
    }

    @Test
    void aggregatesNativeCurrenciesWithoutCombiningThemAndScopesEveryQueryToCaller() {
        UUID userId = UUID.randomUUID();
        WalletCurrencySummaryRow vnd = currency("VND", "250000", "125000", "30000", "70000", "40000");
        WalletCurrencySummaryRow usd = currency("USD", "40.50", "12.50", "0", "18", "18");
        when(walletRepository.findCurrencySummary(userId)).thenReturn(List.of(vnd, usd));
        WalletCategorySummaryRow food = new WalletCategorySummaryRow();
        food.setCurrency("VND");
        food.setCategory("FOOD");
        food.setAmount(new BigDecimal("150000"));
        when(walletRepository.findCategorySummary(userId)).thenReturn(List.of(food));
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
        assertThat(response.getCategoryBreakdown()).singleElement().satisfies(category -> {
            assertThat(category.getCurrency()).isEqualTo("VND");
            assertThat(category.getAmount()).isEqualByComparingTo("150000");
        });
        verify(walletRepository).findExpenses(userId, 30, 0);
        verify(walletRepository).countExpenses(userId);
    }

    @Test
    void rejectsReminderWhenCallerIsNotTheRegisteredPayer() {
        UUID userId = UUID.randomUUID();
        WalletReminderSplitRow row = split(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "USD", "10");
        row.setPaidBy(UUID.randomUUID());
        when(walletRepository.findReminders(userId, "request-1")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(userId, List.of(row.getSplitId()))).thenReturn(List.of(row));

        assertThatThrownBy(() -> service.sendReminders(List.of(row.getSplitId()), "request-1", userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("registered payer");
        verify(walletRepository, never()).saveReminders(any());
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendsOneNotificationForEverySplitOfOnePersonWithTheirTotal() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        WalletReminderSplitRow dinner = split(payer, recipient, tripId, "VND", "150000");
        dinner.setExpenseDescription("Dinner");
        WalletReminderSplitRow taxi = split(payer, recipient, tripId, "VND", "50000");
        taxi.setExpenseDescription("Taxi");
        List<UUID> ids = List.of(dinner.getSplitId(), taxi.getSplitId());
        givenRecipientCanOpen(tripId, payer, recipient);
        when(walletRepository.findReminders(payer, "request-2")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, ids)).thenReturn(List.of(dinner, taxi));
        when(walletRepository.saveReminders(any())).thenReturn(2);
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_DAILY_MAX)).thenReturn(2);
        when(walletRepository.claimDailyReminder(eq(payer), eq(recipient), any(), eq(2))).thenReturn(true);

        var response = service.sendReminders(ids, "request-2", payer);

        assertThat(response.getRecipientUserId()).isEqualTo(recipient);
        assertThat(response.getSplitIds()).containsExactlyElementsOf(ids);
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications, times(1)).emitGeneric(eq(tripId), eq(payer), eq(NotificationType.PAYMENT_REMINDER),
                data.capture(), eq(List.of(recipient)), eq(null));
        assertThat(data.getValue())
                .containsEntry("expenseCount", 2)
                .containsEntry("currency", "VND")
                .containsEntry("expenseDescription", "Dinner, Taxi")
                .containsEntry("deepLink", "/wallet")
                .doesNotContainKey("expenseId");
        assertThat((BigDecimal) data.getValue().get("amount")).isEqualByComparingTo("200000");
    }

    @Test
    void singleSplitReminderKeepsTheExpensePayloadAndLink() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        WalletReminderSplitRow dinner = split(payer, recipient, tripId, "USD", "12.50");
        givenRecipientCanOpen(tripId, payer, recipient);
        when(walletRepository.findReminders(payer, "legacy")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, List.of(dinner.getSplitId()))).thenReturn(List.of(dinner));
        when(walletRepository.saveReminders(any())).thenReturn(1);
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_DAILY_MAX)).thenReturn(2);
        when(walletRepository.claimDailyReminder(eq(payer), eq(recipient), any(), eq(2))).thenReturn(true);

        var response = service.sendReminder(dinner.getExpenseId(), dinner.getSplitId(), "legacy", payer);

        assertThat(response.getExpenseId()).isEqualTo(dinner.getExpenseId());
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).emitGeneric(eq(tripId), eq(payer), eq(NotificationType.PAYMENT_REMINDER),
                data.capture(), eq(List.of(recipient)), eq(null));
        assertThat(data.getValue())
                .containsEntry("expenseCount", 1)
                .containsEntry("expenseId", dinner.getExpenseId().toString())
                .containsEntry("deepLink", "/trip/" + tripId + "/expenses/" + dinner.getExpenseId());
    }

    @Test
    void leavesTheTotalOutWhenTheSplitsUseDifferentCurrencies() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        WalletReminderSplitRow vnd = split(payer, recipient, tripId, "VND", "150000");
        WalletReminderSplitRow usd = split(payer, recipient, tripId, "USD", "20");
        List<UUID> ids = List.of(vnd.getSplitId(), usd.getSplitId());
        givenRecipientCanOpen(tripId, payer, recipient);
        when(walletRepository.findReminders(payer, "mixed")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, ids)).thenReturn(List.of(vnd, usd));
        when(walletRepository.saveReminders(any())).thenReturn(2);
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_DAILY_MAX)).thenReturn(2);
        when(walletRepository.claimDailyReminder(eq(payer), eq(recipient), any(), eq(2))).thenReturn(true);

        service.sendReminders(ids, "mixed", payer);

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).emitGeneric(any(), any(), any(), data.capture(), any(), any());
        assertThat(data.getValue()).containsEntry("amount", "").containsEntry("currency", "");
    }

    @Test
    void rejectsSplitsOfDifferentPeopleInOneReminder() {
        UUID payer = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        WalletReminderSplitRow first = split(payer, UUID.randomUUID(), tripId, "VND", "1");
        WalletReminderSplitRow second = split(payer, UUID.randomUUID(), tripId, "VND", "1");
        List<UUID> ids = List.of(first.getSplitId(), second.getSplitId());
        when(walletRepository.findReminders(payer, "two-people")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, ids)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.sendReminders(ids, "two-people", payer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("one person");
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dropsSettledSplitsAndRejectsWhenNothingIsLeft() {
        UUID payer = UUID.randomUUID();
        WalletReminderSplitRow settled = split(payer, UUID.randomUUID(), UUID.randomUUID(), "VND", "1");
        settled.setIsSettled(true);
        when(walletRepository.findReminders(payer, "settled")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, List.of(settled.getSplitId()))).thenReturn(List.of(settled));

        assertThatThrownBy(() -> service.sendReminders(List.of(settled.getSplitId()), "settled", payer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nothing is left");
        verify(walletRepository, never()).saveReminders(any());
    }

    @Test
    void returnsPriorIdempotentReminderWithoutSendingItAgain() {
        UUID sender = UUID.randomUUID();
        ExpensePaymentReminder saved = ExpensePaymentReminder.builder().id(UUID.randomUUID())
                .senderUserId(sender).recipientUserId(UUID.randomUUID()).expenseId(UUID.randomUUID())
                .splitId(UUID.randomUUID()).idempotencyKey("same-key").createdAt(LocalDateTime.now()).build();
        when(walletRepository.findReminders(sender, "same-key")).thenReturn(List.of(saved));

        assertThat(service.sendReminders(List.of(saved.getSplitId()), "same-key", sender).getSplitIds())
                .containsExactly(saved.getSplitId());
        verify(walletRepository, never()).claimDailyReminder(any(), any(), any(), anyInt());
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rateLimitsThePersonOnceTheDailyLimitIsReached() {
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        WalletReminderSplitRow row = split(payer, recipient, tripId, "VND", "1");
        givenRecipientCanOpen(tripId, payer, recipient);
        when(walletRepository.findReminders(payer, "request-3")).thenReturn(List.of());
        when(walletRepository.findReminderSplits(payer, List.of(row.getSplitId()))).thenReturn(List.of(row));
        when(walletRepository.saveReminders(any())).thenReturn(1);
        when(config.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_DAILY_MAX)).thenReturn(2);
        when(walletRepository.claimDailyReminder(eq(payer), eq(recipient), any(), eq(2))).thenReturn(false);

        assertThatThrownBy(() -> service.sendReminders(List.of(row.getSplitId()), "request-3", payer))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.EXPENSE_PAYMENT_REMINDER_RATE_LIMITED);
        verify(notifications, never()).emitGeneric(any(), any(), any(), any(), any(), any());
    }

    private WalletReminderSplitRow split(UUID payer, UUID recipient, UUID tripId, String currency, String amount) {
        WalletReminderSplitRow row = new WalletReminderSplitRow();
        row.setSplitId(UUID.randomUUID());
        row.setExpenseId(UUID.randomUUID());
        row.setTripId(tripId);
        row.setRecipientUserId(recipient);
        row.setPaidBy(payer);
        row.setIsSettled(false);
        row.setCurrency(currency);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private void givenRecipientCanOpen(UUID tripId, UUID payer, UUID recipient) {
        when(accessGuard.requireAccess(tripId, payer)).thenReturn(Trip.builder().id(tripId).ownerId(recipient).build());
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
