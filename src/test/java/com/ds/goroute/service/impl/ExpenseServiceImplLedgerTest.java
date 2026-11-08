package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.ExpensePayerRequest;
import com.ds.goroute.dto.request.ExpenseSplitRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpensePayer;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Settlement;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpensePayerRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.SettlementRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.expense.ExpenseLedgerStore;
import com.ds.goroute.service.expense.ExpenseParticipants;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.SettlementMethod;
import com.ds.goroute.type.SplitMethod;
import com.ds.goroute.type.TripRealtimeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Trip expenses on the ledger: who paid, who shares, and an old build's "mark as paid". */
@SuppressWarnings("unchecked")
class ExpenseServiceImplLedgerTest {

    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final ExpenseSplitRepository splits = mock(ExpenseSplitRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ExchangeRateService rates = mock(ExchangeRateService.class);
    private final ExpensePayerRepository payers = mock(ExpensePayerRepository.class);
    private final SettlementRepository settlements = mock(SettlementRepository.class);
    private final TripRealtimePublisher realtime = mock(TripRealtimePublisher.class);
    private ExpenseServiceImpl service;

    private final UUID tripId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final Trip trip = Trip.builder().id(tripId).ownerId(owner).currency("VND").build();

    @BeforeEach
    void setUp() {
        ExpenseParticipants participants = new ExpenseParticipants(members, users);
        service = new ExpenseServiceImpl(expenses, splits, mock(ActivityRepository.class), accessGuard, trips,
                members, users, mock(NotificationHelper.class), rates, mock(ImageStorageCleanupService.class),
                mock(MediaAssetRepository.class), realtime, new ExpenseLedgerStore(splits, payers, settlements),
                participants, mock(PlatformTransactionManager.class));
        when(accessGuard.requireEditAccess(tripId, owner)).thenReturn(trip);
        when(accessGuard.requireAccess(tripId, owner)).thenReturn(trip);
        when(accessGuard.requireAccess(tripId, member)).thenReturn(trip);
        when(trips.findById(tripId)).thenReturn(Optional.of(trip));
        when(members.findByTripId(tripId)).thenReturn(List.of(
                TripMember.builder().id(UUID.randomUUID()).tripId(tripId).userId(member)
                        .status(MemberStatus.ACCEPTED).isGuest(false).build(),
                TripMember.builder().id(guestId).tripId(tripId).guestName("Lan").isGuest(true).build()));
        when(members.findByIds(anyCollection())).thenReturn(List.of());
        when(users.findByIds(anyCollection())).thenReturn(List.of());
        when(payers.findByExpenseIds(anyCollection())).thenReturn(List.of());
        when(splits.findByExpenseIds(anyCollection())).thenReturn(List.of());
        when(splits.findByExpenseId(any())).thenReturn(List.of());
        when(settlements.findActiveBySplitIds(anyCollection())).thenReturn(List.of());
    }

    private static ExpenseSplitRequest share(UUID userId, UUID guestMemberId, String amount, String shareInput) {
        return ExpenseSplitRequest.builder()
                .userId(userId)
                .guestMemberId(guestMemberId)
                .amount(amount == null ? null : new BigDecimal(amount))
                .shareInput(shareInput == null ? null : new BigDecimal(shareInput))
                .build();
    }

    private CreateExpenseRequest.CreateExpenseRequestBuilder expense(String amount) {
        return CreateExpenseRequest.builder()
                .amount(new BigDecimal(amount))
                .currency("VND")
                .category(ExpenseCategory.FOOD)
                .description("Lẩu");
    }

    private List<ExpenseSplit> savedSplits() {
        ArgumentCaptor<ExpenseSplit> captor = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(splits, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("an old build's unrounded amount / n is stored as whole dong adding up to the amount")
    void legacyEqualSplitIsRounded() {
        service.createExpense(tripId, expense("100000").paidBy(owner).splits(List.of(
                share(owner, null, "33333.333333", null),
                share(member, null, "33333.333333", null),
                share(null, guestId, "33333.333333", null))).build(), owner);

        List<ExpenseSplit> stored = savedSplits();
        assertThat(stored).hasSize(3);
        assertThat(stored.stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100000");
        assertThat(stored.get(0).getIsSettled()).as("the payer's own share").isTrue();
        assertThat(stored.get(1).getIsSettled()).isFalse();
        verify(realtime).publishAfterCommit(eq(TripRealtimeEventType.LEDGER_UPDATED), eq(tripId), any(), eq(owner));
    }

    @Test
    @DisplayName("shares that do not add up to the amount are refused and nothing is written")
    void wrongSumIsRefused() {
        assertThatThrownBy(() -> service.createExpense(tripId, expense("100000").paidBy(owner).splits(List.of(
                share(owner, null, "50000", null),
                share(member, null, "20000", null))).build(), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("add up");
        verify(expenses, never()).insert(any());
    }

    @Test
    @DisplayName("a payer or share outside the trip is refused")
    void outsiderIsRefused() {
        assertThatThrownBy(() -> service.createExpense(tripId, expense("100000").paidBy(stranger).splits(List.of(
                share(owner, null, "100000", null))).build(), owner))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createExpense(tripId, expense("100000").paidBy(owner).splits(List.of(
                share(stranger, null, "100000", null))).build(), owner))
                .isInstanceOf(BusinessException.class);
        verify(expenses, never()).insert(any());
    }

    @Test
    @DisplayName("several payers and a percentage split: the server computes every share")
    void severalPayersAndPercentages() {
        service.createExpense(tripId, expense("300000")
                .payers(List.of(
                        ExpensePayerRequest.builder().userId(member).amount(new BigDecimal("200000")).build(),
                        ExpensePayerRequest.builder().guestMemberId(guestId).amount(new BigDecimal("100000")).build()))
                .splitMethod(SplitMethod.PERCENT)
                .splits(List.of(
                        share(owner, null, null, "50"),
                        share(member, null, null, "25"),
                        share(null, guestId, null, "25")))
                .build(), owner);

        ArgumentCaptor<List<ExpensePayer>> payerRows = ArgumentCaptor.forClass(List.class);
        verify(payers).saveAll(payerRows.capture());
        assertThat(payerRows.getValue()).extracting(ExpensePayer::getAmount)
                .containsExactly(new BigDecimal("200000"), new BigDecimal("100000"));
        assertThat(payerRows.getValue().get(1).getGuestMemberId()).isEqualTo(guestId);

        assertThat(savedSplits()).extracting(ExpenseSplit::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("150000"), new BigDecimal("75000"), new BigDecimal("75000"));

        ArgumentCaptor<Expense> inserted = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).insert(inserted.capture());
        assertThat(inserted.getValue().getPaidBy()).as("old builds see the largest payer").isEqualTo(member);
        assertThat(inserted.getValue().getSplitMethod()).isEqualTo(SplitMethod.PERCENT);
    }

    @Test
    @DisplayName("payers who do not add up to the amount are refused")
    void payersMustAddUp() {
        assertThatThrownBy(() -> service.createExpense(tripId, expense("300000")
                .payers(List.of(ExpensePayerRequest.builder().userId(member).amount(new BigDecimal("100000")).build()))
                .splitMethod(SplitMethod.EQUAL)
                .splits(List.of(share(owner, null, null, null), share(member, null, null, null)))
                .build(), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("add up");
    }

    @Test
    @DisplayName("no exchange rate for a foreign currency is an error, never a 1:1 rate")
    void missingRateIsAnError() {
        when(rates.findRateOn(eq("JPY"), eq("VND"), any(LocalDate.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createExpense(tripId, expense("1000").currency("JPY").paidBy(owner)
                .splits(List.of(share(owner, null, "1000", null))).build(), owner))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.EXCHANGE_RATE_UNAVAILABLE));
        verify(expenses, never()).insert(any());
    }

    @Test
    @DisplayName("a foreign currency is priced at the rate of the day it was spent")
    void foreignCurrencyUsesTheRate() {
        when(rates.findRateOn(eq("JPY"), eq("VND"), any(LocalDate.class))).thenReturn(Optional.of(new BigDecimal("170")));

        service.createExpense(tripId, expense("1000").currency("JPY").paidBy(owner)
                .splits(List.of(share(owner, null, "500", null), share(member, null, "500", null))).build(), owner);

        ArgumentCaptor<Expense> inserted = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).insert(inserted.capture());
        assertThat(inserted.getValue().getExchangeRate()).isEqualByComparingTo("170");
        assertThat(inserted.getValue().getAmountInTripCurrency()).isEqualByComparingTo("170000");
    }

    @Test
    @DisplayName("an old build's 'paid' on a share records the payment to the payer, and 'not paid' removes it")
    void legacyMarkPaidWritesASettlement() {
        UUID expenseId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        Expense stored = Expense.builder().id(expenseId).tripId(tripId).amount(new BigDecimal("100000"))
                .currency("VND").category(ExpenseCategory.FOOD).paidBy(owner).build();
        ExpenseSplit split = ExpenseSplit.builder().id(splitId).expenseId(expenseId).userId(member)
                .amount(new BigDecimal("50000")).isSettled(false).build();
        when(expenses.findById(expenseId)).thenReturn(Optional.of(stored));
        when(splits.findById(splitId)).thenReturn(split);

        service.markPaymentForSplit(tripId, expenseId, splitId, MarkPaymentRequest.builder().isPaid(true).build(), owner);

        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).insert(settlement.capture());
        assertThat(settlement.getValue().getFromUserId()).isEqualTo(member);
        assertThat(settlement.getValue().getToUserId()).isEqualTo(owner);
        assertThat(settlement.getValue().getAmount()).isEqualByComparingTo("50000");
        assertThat(settlement.getValue().getSplitId()).isEqualTo(splitId);
        assertThat(settlement.getValue().getMethod()).isEqualTo(SettlementMethod.OTHER);
        assertThat(split.getIsSettled()).isTrue();

        service.markPaymentForSplit(tripId, expenseId, splitId, MarkPaymentRequest.builder().isPaid(false).build(), owner);

        ArgumentCaptor<Collection<UUID>> unlinked = ArgumentCaptor.forClass(Collection.class);
        verify(settlements, org.mockito.Mockito.atLeastOnce()).softDeleteBySplitIds(unlinked.capture());
        List<UUID> all = new ArrayList<>();
        unlinked.getAllValues().forEach(all::addAll);
        assertThat(all).contains(splitId);
        assertThat(split.getIsSettled()).isFalse();
    }

    @Test
    @DisplayName("only the payer or the share's own person may mark it paid")
    void othersCannotMarkPaid() {
        UUID expenseId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        when(expenses.findById(expenseId)).thenReturn(Optional.of(Expense.builder().id(expenseId).tripId(tripId)
                .amount(new BigDecimal("100000")).currency("VND").category(ExpenseCategory.FOOD).paidBy(owner).build()));
        when(splits.findById(splitId)).thenReturn(ExpenseSplit.builder().id(splitId).expenseId(expenseId)
                .guestMemberId(guestId).amount(new BigDecimal("50000")).isSettled(false).build());

        assertThatThrownBy(() -> service.markPaymentForSplit(tripId, expenseId, splitId,
                MarkPaymentRequest.builder().isPaid(true).build(), member))
                .isInstanceOf(BusinessException.class);
        verify(settlements, never()).insert(any());
    }
}
