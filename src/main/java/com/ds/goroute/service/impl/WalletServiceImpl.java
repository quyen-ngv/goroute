package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.WalletCurrencySummaryResponse;
import com.ds.goroute.dto.response.WalletCategorySummaryResponse;
import com.ds.goroute.dto.response.WalletDebtResponse;
import com.ds.goroute.dto.response.WalletExpenseResponse;
import com.ds.goroute.dto.response.WalletExpenseSplitResponse;
import com.ds.goroute.dto.response.WalletPersonReminderResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.dto.response.WalletReminderResponse;
import com.ds.goroute.dto.response.WalletResponse;
import com.ds.goroute.entity.ExpensePaymentReminder;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.WalletCurrencySummaryRow;
import com.ds.goroute.entity.WalletDebtRow;
import com.ds.goroute.entity.WalletExpenseRow;
import com.ds.goroute.entity.WalletReminderSplitRow;
import com.ds.goroute.exception.BusinessException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    /** A reminder day, for the per-person daily limit. */
    private static final ZoneId REMINDER_DAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Expenses named in a grouped reminder before the rest become "+N". */
    private static final int DESCRIBED_EXPENSES = 3;

    private final WalletRepository walletRepository;
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
                .categoryBreakdown(walletRepository.findCategorySummary(userId).stream()
                        .map(row -> WalletCategorySummaryResponse.builder()
                                .currency(row.getCurrency()).category(row.getCategory())
                                .amount(zero(row.getAmount())).build()).toList())
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
     * Legacy single-split reminder, kept for app builds that still remind split by split.
     * It goes through the same per-person daily limit as {@link #sendReminders}.
     */
    @Override
    @Transactional
    public WalletReminderResponse sendReminder(UUID expenseId, UUID splitId, String idempotencyKey, UUID userId) {
        return toReminder(remind(List.of(splitId), expenseId, idempotencyKey, userId).get(0));
    }

    /**
     * Reminds one person about every open split the caller paid for, as a single notification.
     * Only the registered payer may remind, only a registered recipient can be reminded, and a
     * payer may remind the same person at most {@code EXPENSE.PAYMENT_REMINDER_DAILY_MAX} times
     * per day. Guest splits stay visible in Wallet but have no delivery target.
     */
    @Override
    @Transactional
    public WalletPersonReminderResponse sendReminders(List<UUID> splitIds, String idempotencyKey, UUID userId) {
        List<ExpensePaymentReminder> sent = remind(splitIds, null, idempotencyKey, userId);
        return WalletPersonReminderResponse.builder()
                .recipientUserId(sent.get(0).getRecipientUserId())
                .splitIds(sent.stream().map(ExpensePaymentReminder::getSplitId).toList())
                .sentAt(sent.get(0).getCreatedAt())
                .build();
    }

    private List<ExpensePaymentReminder> remind(List<UUID> splitIds, UUID expectedExpenseId,
                                                String idempotencyKey, UUID userId) {
        List<UUID> requested = splitIds.stream().distinct().toList();
        List<ExpensePaymentReminder> prior = walletRepository.findReminders(userId, idempotencyKey);
        if (!prior.isEmpty()) {
            return replay(prior, requested);
        }

        List<WalletReminderSplitRow> rows = walletRepository.findReminderSplits(userId, requested);
        if (rows.size() != requested.size()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense split not found");
        }
        UUID recipient = rows.get(0).getRecipientUserId();
        for (WalletReminderSplitRow row : rows) {
            if (expectedExpenseId != null && !expectedExpenseId.equals(row.getExpenseId())) {
                throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense split not found");
            }
            if (row.getPaidByGuestMemberId() != null || !userId.equals(row.getPaidBy())) {
                throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                        "Only the registered payer can send a payment reminder");
            }
            if (row.getRecipientUserId() == null || row.getRecipientUserId().equals(userId)) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Only an outstanding registered member split can be reminded");
            }
            if (!row.getRecipientUserId().equals(recipient)) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "One payment reminder covers the splits of one person");
            }
        }

        // Settled since Wallet loaded, or in a trip the recipient has left: nothing to remind them of there.
        Map<UUID, Boolean> recipientCanOpenTrip = new HashMap<>();
        List<WalletReminderSplitRow> due = rows.stream()
                .filter(row -> !Boolean.TRUE.equals(row.getIsSettled()))
                .filter(row -> recipientCanOpenTrip.computeIfAbsent(row.getTripId(),
                        tripId -> recipientCanStillOpenTrip(tripAccessGuard.requireAccess(tripId, userId), recipient)))
                .toList();
        if (due.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Nothing is left to remind this person about");
        }

        LocalDateTime now = LocalDateTime.now();
        List<ExpensePaymentReminder> reminders = due.stream()
                .map(row -> ExpensePaymentReminder.builder()
                        .id(UUID.randomUUID())
                        .senderUserId(userId)
                        .recipientUserId(recipient)
                        .expenseId(row.getExpenseId())
                        .splitId(row.getSplitId())
                        .idempotencyKey(idempotencyKey)
                        .createdAt(now)
                        .build())
                .toList();
        if (walletRepository.saveReminders(reminders) == 0) {
            // A concurrent retry with the same key committed first; answer with what it sent.
            return replay(walletRepository.findReminders(userId, idempotencyKey), requested);
        }

        int dailyMax = businessConfigService.getInt(BusinessConfigKey.EXPENSE_PAYMENT_REMINDER_DAILY_MAX);
        if (!walletRepository.claimDailyReminder(userId, recipient, LocalDate.now(REMINDER_DAY_ZONE), dailyMax)) {
            // Rolls back the ledger rows written above.
            throw new BusinessException(ErrorConstant.EXPENSE_PAYMENT_REMINDER_RATE_LIMITED,
                    "This person was already reminded " + dailyMax + " times today");
        }

        notificationHelper.emitGeneric(due.get(0).getTripId(), userId, NotificationType.PAYMENT_REMINDER,
                reminderData(due, userId), List.of(recipient), null);
        return reminders;
    }

    private List<ExpensePaymentReminder> replay(List<ExpensePaymentReminder> prior, List<UUID> requested) {
        if (prior.isEmpty() || !requested.containsAll(prior.stream().map(ExpensePaymentReminder::getSplitId).toList())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Idempotency-Key was already used for another payment reminder");
        }
        return prior;
    }

    /**
     * One expense keeps the original payload and opens that expense. Several carry
     * {@code expenseCount}, a total when they share a currency, and open Wallet, where the
     * recipient sees what they owe grouped by person. {@code expenseDescription} lists the
     * expenses so app builds that only know the single-expense text still read sensibly.
     */
    private Map<String, Object> reminderData(List<WalletReminderSplitRow> due, UUID userId) {
        WalletReminderSplitRow first = due.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorName", notificationHelper.actorName(userId));
        data.put("expenseCount", due.size());
        data.put("expenseDescription", describe(due));
        Set<String> currencies = due.stream().map(WalletReminderSplitRow::getCurrency).collect(Collectors.toSet());
        if (currencies.size() == 1) {
            data.put("amount", due.stream().map(row -> zero(row.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            data.put("currency", first.getCurrency());
        } else {
            data.put("amount", "");
            data.put("currency", "");
        }
        if (due.size() == 1) {
            data.put("expenseId", first.getExpenseId().toString());
            data.put("deepLink", "/trip/" + first.getTripId() + "/expenses/" + first.getExpenseId());
        } else {
            data.put("deepLink", "/wallet");
        }
        return data;
    }

    private String describe(List<WalletReminderSplitRow> due) {
        List<String> names = due.stream().map(row -> safeDescription(row.getExpenseDescription())).distinct().toList();
        String shown = String.join(", ", names.subList(0, Math.min(names.size(), DESCRIBED_EXPENSES)));
        return names.size() > DESCRIBED_EXPENSES ? shown + " +" + (names.size() - DESCRIBED_EXPENSES) : shown;
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
