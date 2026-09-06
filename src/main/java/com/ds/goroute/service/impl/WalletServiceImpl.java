package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.WalletCurrencySummaryResponse;
import com.ds.goroute.dto.response.WalletDebtResponse;
import com.ds.goroute.dto.response.WalletExpenseResponse;
import com.ds.goroute.dto.response.WalletExpenseSplitResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.dto.response.WalletReminderResponse;
import com.ds.goroute.dto.response.WalletResponse;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.WalletRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.WalletService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard tripAccessGuard;
    private final BusinessConfigService businessConfigService;
    private final NotificationHelper notificationHelper;

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId, int page, int pageSize, int debtLimit) {
        int offset = page * pageSize;
        return WalletResponse.builder()
                .currencies(walletRepository.findCurrencySummary(userId).stream().map(this::toCurrency).toList())
                .owedToMe(walletRepository.findOwedToMe(userId, debtLimit).stream()
                        .map(row -> toDebt(row, row.getCounterpartUserId() != null)).toList())
                .iOwe(walletRepository.findIOwe(userId, debtLimit).stream()
                        .map(row -> toDebt(row, false)).toList())
                .expenses(groupExpenses(walletRepository.findExpenses(userId, pageSize, offset)))
                .page(page)
                .pageSize(pageSize)
                .totalItems(walletRepository.countExpenses(userId))
                .build();
    }

    /**
     * Sends a reminder only when the caller is the registered payer. Guest splits stay visible
     * in Wallet but deliberately have no delivery target, even if their profile is later absent.
     */
    @Override
    @Transactional
    public WalletReminderResponse sendReminder(UUID expenseId, UUID splitId, String idempotencyKey, UUID userId) {
        var prior = walletRepository.findReminder(userId, idempotencyKey);
        if (prior.isPresent()) {
            ExpensePaymentReminder reminder = prior.get();
            if (!reminder.getExpenseId().equals(expenseId) || !reminder.getSplitId().equals(splitId)) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Idempotency-Key was already used for another payment reminder");
            }
            return toReminder(reminder);
        }

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));
        Trip trip = tripAccessGuard.requireAccess(expense.getTripId(), userId);
        if (expense.getPaidByGuestMemberId() != null || !userId.equals(expense.getPaidBy())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the registered payer can send a payment reminder");
        }

        ExpenseSplit split = expenseSplitRepository.findById(splitId);
        if (split == null || !expenseId.equals(split.getExpenseId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense split not found");
        }
        if (Boolean.TRUE.equals(split.getIsSettled())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "This payment has already been settled");
        }
        if (split.getUserId() == null || split.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only an outstanding registered member split can be reminded");
        }
        if (!recipientCanStillOpenTrip(trip, split.getUserId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "The recipient no longer has access to this trip");
        }

        LocalDateTime now = LocalDateTime.now();
        int cooldownMinutes = businessConfigService.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_COOLDOWN_MINUTES);
        if (!walletRepository.claimReminderCooldown(userId, splitId, now, now.minusMinutes(cooldownMinutes))) {
            // A concurrent retry with the same key may have inserted the ledger after our first read.
            var racedReminder = walletRepository.findReminder(userId, idempotencyKey);
            if (racedReminder.isPresent()) {
                return toReminder(racedReminder.get());
            }
            throw new BusinessException(ErrorConstant.EXPENSE_PAYMENT_REMINDER_RATE_LIMITED,
                    "A reminder was already sent recently for this payment");
        }

        ExpensePaymentReminder reminder = ExpensePaymentReminder.builder()
                .id(UUID.randomUUID())
                .senderUserId(userId)
                .recipientUserId(split.getUserId())
                .expenseId(expenseId)
                .splitId(splitId)
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .build();
        walletRepository.saveReminder(reminder);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorName", notificationHelper.actorName(userId));
        data.put("expenseDescription", safeDescription(expense.getDescription()));
        data.put("amount", split.getAmount());
        data.put("currency", expense.getCurrency());
        data.put("expenseId", expenseId.toString());
        data.put("deepLink", "/trip/" + trip.getId() + "/expenses/" + expenseId);
        notificationHelper.emitGeneric(trip.getId(), userId, NotificationType.PAYMENT_REMINDER,
                data, List.of(split.getUserId()), null);
        return toReminder(reminder);
    }

    private boolean recipientCanStillOpenTrip(Trip trip, UUID recipientUserId) {
        if (recipientUserId.equals(trip.getOwnerId())) return true;
        return tripMemberRepository.findByTripIdAndUserId(trip.getId(), recipientUserId)
                .map(member -> MemberStatus.ACCEPTED.equals(member.getStatus()))
                .orElse(false);
    }

    private List<WalletExpenseResponse> groupExpenses(List<WalletExpenseRow> rows) {
        Map<UUID, WalletExpenseResponse> result = new LinkedHashMap<>();
        Map<UUID, List<WalletExpenseSplitResponse>> splitsByExpense = new LinkedHashMap<>();
        for (WalletExpenseRow row : rows) {
            result.computeIfAbsent(row.getExpenseId(), ignored -> WalletExpenseResponse.builder()
                    .id(row.getExpenseId())
                    .tripId(row.getTripId())
                    .tripName(row.getTripName())
                    .amount(zero(row.getAmount()))
                    .currency(row.getCurrency())
                    .category(row.getCategory() == null ? null : row.getCategory().name())
                    .description(row.getDescription())
                    .activityId(row.getActivityId())
                    .paidBy(toPerson(row.getPaidByUserId(), row.getPaidByGuestMemberId(),
                            row.getPaidByDisplayName(), row.getPaidByAvatarUrl()))
                    .splits(new ArrayList<>())
                    .createdAt(row.getCreatedAt())
                    .build());
            if (row.getSplitId() != null) {
                splitsByExpense.computeIfAbsent(row.getExpenseId(), ignored -> new ArrayList<>())
                        .add(WalletExpenseSplitResponse.builder()
                                .id(row.getSplitId())
                                .person(toPerson(row.getSplitUserId(), row.getSplitGuestMemberId(),
                                        row.getSplitDisplayName(), row.getSplitAvatarUrl()))
                                .amount(zero(row.getSplitAmount()))
                                .isSettled(Boolean.TRUE.equals(row.getSplitSettled()))
                                .settledAt(row.getSplitSettledAt())
                                .build());
            }
        }
        result.forEach((expenseId, expense) -> expense.setSplits(splitsByExpense.getOrDefault(expenseId, List.of())));
        return new ArrayList<>(result.values());
    }

    private WalletCurrencySummaryResponse toCurrency(WalletCurrencySummaryRow row) {
        return WalletCurrencySummaryResponse.builder().currency(row.getCurrency())
                .totalSpent(zero(row.getTotalSpent()))
                .owedToMe(zero(row.getOwedToMe())).iOwe(zero(row.getIOwe()))
                .myShare(zero(row.getMyShare())).myPaid(zero(row.getMyPaid())).build();
    }

    private WalletDebtResponse toDebt(WalletDebtRow row, boolean canRemind) {
        return WalletDebtResponse.builder().splitId(row.getSplitId()).expenseId(row.getExpenseId())
                .tripId(row.getTripId()).tripName(row.getTripName()).expenseDescription(row.getExpenseDescription())
                .amount(zero(row.getAmount())).currency(row.getCurrency())
                .counterpart(toPerson(row.getCounterpartUserId(), row.getCounterpartGuestMemberId(),
                        row.getCounterpartDisplayName(), row.getCounterpartAvatarUrl()))
                .isSettled(false).canRemind(canRemind).createdAt(row.getCreatedAt()).build();
    }

    private WalletPersonResponse toPerson(UUID userId, UUID guestMemberId, String name, String avatarUrl) {
        boolean guest = userId == null;
        return WalletPersonResponse.builder().userId(userId).guestMemberId(guestMemberId)
                .displayName(name == null || name.isBlank() ? "Guest" : name)
                .avatarUrl(guest ? null : avatarUrl).isGuest(guest).build();
    }

    private WalletReminderResponse toReminder(ExpensePaymentReminder reminder) {
        return WalletReminderResponse.builder().id(reminder.getId()).expenseId(reminder.getExpenseId())
                .splitId(reminder.getSplitId()).recipientUserId(reminder.getRecipientUserId())
                .sentAt(reminder.getCreatedAt()).build();
    }

    private BigDecimal zero(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
    private String safeDescription(String value) { return value == null || value.isBlank() ? "an expense" : value; }
}
