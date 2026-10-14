package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.PersonalExpenseRequest;
import com.ds.goroute.dto.response.PersonalExpenseResponse;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalExpenseServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID AN = UUID.randomUUID();
    private static final UUID BINH = UUID.randomUUID();

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private ImageStorageCleanupService imageStorageCleanupService;
    @Mock private UserRepository userRepository;
    @Mock private UserBlockService userBlockService;
    @Mock private NotificationService notificationService;

    private PersonalExpenseService service;

    @BeforeEach
    void setUp() {
        service = new PersonalExpenseService(expenseRepository, expenseSplitRepository,
                mediaAssetRepository, imageStorageCleanupService, userRepository, userBlockService,
                notificationService);
        when(mediaAssetRepository.findByEntity(any(), any())).thenReturn(List.of());
        when(userRepository.findByIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().map(id -> User.builder().id(id).username("u" + id.toString().substring(0, 4)).build())
                    .toList();
        });
        when(userRepository.findById(any())).thenAnswer(invocation ->
                Optional.of(User.builder().id(invocation.getArgument(0)).fullName("Me").build()));
        when(userBlockService.blockedAmong(any(), anyCollection())).thenReturn(Set.of());
    }

    private static PersonalExpenseRequest request(String amount) {
        return PersonalExpenseRequest.builder()
                .amount(new BigDecimal(amount))
                .currency(" vnd ")
                .category(ExpenseCategory.FOOD)
                .description("  Lẩu ")
                .build();
    }

    private static PersonalExpenseRequest.Participant person(UUID id, String amount) {
        return PersonalExpenseRequest.Participant.builder()
                .userId(id).amount(amount == null ? null : new BigDecimal(amount)).build();
    }

    private List<ExpenseSplit> savedSplits(int count) {
        ArgumentCaptor<ExpenseSplit> captor = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(expenseSplitRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void aloneItIsTheCreatorsOwnSettledSpending() {
        PersonalExpenseResponse response = service.create(request("120000"), ME);

        ArgumentCaptor<Expense> expense = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).insert(expense.capture());
        assertThat(expense.getValue().getTripId()).isNull();
        assertThat(expense.getValue().getPaidBy()).isEqualTo(ME);
        assertThat(expense.getValue().getCurrency()).isEqualTo("VND");

        List<ExpenseSplit> splits = savedSplits(1);
        assertThat(splits.get(0).getUserId()).isEqualTo(ME);
        assertThat(splits.get(0).getIsSettled()).isTrue();
        assertThat(response.getDescription()).isEqualTo("Lẩu");
        assertThat(response.isCanEdit()).isTrue();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anEqualSplitGivesTheRemainderToTheLastShareAndOnlyThePayerIsSettled() {
        PersonalExpenseRequest request = request("100000");
        request.setParticipants(List.of(person(ME, null), person(AN, null), person(BINH, null)));

        service.create(request, ME);

        List<ExpenseSplit> splits = savedSplits(3);
        assertThat(splits).extracting(ExpenseSplit::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("33333"), new BigDecimal("33333"), new BigDecimal("33334"));
        assertThat(splits).extracting(ExpenseSplit::getIsSettled).containsExactly(true, false, false);
        verify(notificationService, times(2)).createNotification(any(), isNull(),
                eq(NotificationType.EXPENSE_ADDED), isNull(), isNull(), any(), eq(ME));
    }

    @Test
    void aFriendCanBeThePayerAndThenTheCreatorOwesThem() {
        PersonalExpenseRequest request = request("200000");
        request.setPaidBy(AN);
        request.setSplitType("CUSTOM");
        request.setParticipants(List.of(person(ME, "150000"), person(AN, "50000")));

        service.create(request, ME);

        List<ExpenseSplit> splits = savedSplits(2);
        assertThat(splits.get(0).getUserId()).isEqualTo(ME);
        assertThat(splits.get(0).getIsSettled()).isFalse();
        assertThat(splits.get(1).getIsSettled()).isTrue();
    }

    @Test
    void rejectsSharesThatDoNotAddUpAPayerOutsideTheSplitAndBlockedPeople() {
        PersonalExpenseRequest wrongSum = request("100");
        wrongSum.setSplitType("CUSTOM");
        wrongSum.setParticipants(List.of(person(ME, "40"), person(AN, "50")));
        assertThatThrownBy(() -> service.create(wrongSum, ME)).isInstanceOf(BusinessException.class);

        PersonalExpenseRequest outsider = request("100");
        outsider.setPaidBy(BINH);
        outsider.setParticipants(List.of(person(ME, null), person(AN, null)));
        assertThatThrownBy(() -> service.create(outsider, ME)).isInstanceOf(BusinessException.class);

        PersonalExpenseRequest notMine = request("100");
        notMine.setPaidBy(AN);
        notMine.setParticipants(List.of(person(AN, null), person(BINH, null)));
        assertThatThrownBy(() -> service.create(notMine, ME)).isInstanceOf(BusinessException.class);

        when(userBlockService.blockedAmong(eq(ME), anyCollection())).thenReturn(Set.of(AN));
        PersonalExpenseRequest blocked = request("100");
        blocked.setParticipants(List.of(person(ME, null), person(AN, null)));
        assertThatThrownBy(() -> service.create(blocked, ME)).isInstanceOf(BusinessException.class);
        verify(expenseRepository, never()).insert(any());
    }

    @Test
    void aParticipantCanReadAndSettleTheirShareButNotEdit() {
        UUID id = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        when(expenseRepository.findById(id)).thenReturn(Optional.of(Expense.builder()
                .id(id).createdBy(ME).paidBy(ME).amount(new BigDecimal("100")).currency("VND").build()));
        ExpenseSplit anShare = ExpenseSplit.builder().id(splitId).expenseId(id).userId(AN)
                .amount(new BigDecimal("50")).isSettled(false).build();
        when(expenseSplitRepository.findByExpenseId(id)).thenReturn(List.of(
                ExpenseSplit.builder().id(UUID.randomUUID()).expenseId(id).userId(ME)
                        .amount(new BigDecimal("50")).isSettled(true).build(),
                anShare));

        PersonalExpenseResponse seen = service.get(id, AN);
        assertThat(seen.isCanEdit()).isFalse();
        assertThat(seen.getSplitType()).isEqualTo("EQUAL");

        assertThatThrownBy(() -> service.update(id, request("100"), AN)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(id, BINH)).isInstanceOf(BusinessException.class);

        service.markSplit(id, splitId, true, AN);
        assertThat(anShare.getIsSettled()).isTrue();
        verify(expenseSplitRepository).update(anShare);
        verify(notificationService).createNotification(eq(ME), isNull(), eq(NotificationType.PAYMENT_MARKED),
                isNull(), isNull(), any(), eq(AN));
    }

    @Test
    void anEditKeepsASettledShareOnlyWhileTheAmountAndPayerStayTheSame() {
        UUID id = UUID.randomUUID();
        when(expenseRepository.findById(id)).thenReturn(Optional.of(Expense.builder()
                .id(id).createdBy(ME).paidBy(ME).amount(new BigDecimal("100")).currency("VND").build()));
        when(expenseSplitRepository.findByExpenseId(id)).thenReturn(List.of(
                ExpenseSplit.builder().id(UUID.randomUUID()).userId(ME).amount(new BigDecimal("50")).isSettled(true).build(),
                ExpenseSplit.builder().id(UUID.randomUUID()).userId(AN).amount(new BigDecimal("50")).isSettled(true)
                        .settledAt(LocalDateTime.now().minusDays(1)).build()));

        PersonalExpenseRequest same = request("100");
        same.setParticipants(List.of(person(ME, null), person(AN, null)));
        service.update(id, same, ME);

        List<ExpenseSplit> kept = savedSplits(2);
        assertThat(kept.get(1).getIsSettled()).isTrue();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnknownCurrencyAndFutureDates() {
        PersonalExpenseRequest badCurrency = request("1");
        badCurrency.setCurrency("XYZ");
        assertThatThrownBy(() -> service.create(badCurrency, ME)).isInstanceOf(BusinessException.class);

        PersonalExpenseRequest future = request("1");
        future.setExpenseDate(LocalDateTime.now().plusDays(3));
        assertThatThrownBy(() -> service.create(future, ME)).isInstanceOf(BusinessException.class);
    }
}
