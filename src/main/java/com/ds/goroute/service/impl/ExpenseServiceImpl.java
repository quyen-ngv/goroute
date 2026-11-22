package com.ds.goroute.service.impl;

import com.ds.goroute.service.TripAccessGuard;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.ExpenseSplitRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpensePayerResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.expense.ExpenseComposer;
import com.ds.goroute.service.expense.ExpenseComposition;
import com.ds.goroute.service.expense.ExpenseComposition.PayerLine;
import com.ds.goroute.service.expense.ExpenseComposition.SplitLine;
import com.ds.goroute.service.expense.ExpenseLedgerStore;
import com.ds.goroute.service.expense.ExpenseParticipants;
import com.ds.goroute.service.expense.ExpenseParticipants.TripRoster;
import com.ds.goroute.service.expense.ParticipantRef;
import com.ds.goroute.service.expense.SplitCalculator;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.SplitMethod;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ActivityRepository activityRepository;
    private final TripAccessGuard tripAccessGuard;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final NotificationHelper notificationHelper;
    private final ExchangeRateService exchangeRateService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final MediaAssetRepository mediaAssetRepository;
    private final TripRealtimePublisher tripRealtimePublisher;
    private final ExpenseLedgerStore ledgerStore;
    private final ExpenseParticipants participants;
    private final PlatformTransactionManager transactionManager;

    /** Receipts live in media_assets alongside trip memories, keyed by this. */
    private static final String EXPENSE_ENTITY_TYPE = "EXPENSE";
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    /** Page size when the caller pages without saying how much; the cap matches the API rule. */
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    /** Reading the budget: owner, or an accepted member of any role. */
    private Trip validateTripAccess(UUID tripId, UUID userId) {
        return tripAccessGuard.requireAccess(tripId, userId);
    }

    /** Writing to the budget: owner or an accepted EDITOR. A viewer may read the totals only. */
    private Trip validateTripEditAccess(UUID tripId, UUID userId) {
        return tripAccessGuard.requireEditAccess(tripId, userId);
    }

    /**
     * Not {@code @Transactional}: the exchange rate may need an HTTP lookup, which must not hold a
     * database connection. The writes run in {@link #inTransaction} afterwards (joining the
     * caller's transaction when there is one, as for the assistant's edits).
     */
    @Override
    public ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId) {
        Trip trip = validateTripEditAccess(tripId, userId);
        LocalDateTime expenseDate = resolveExpenseDate(request.getExpenseDate());
        String currency = currencyCode(request.getCurrency());
        BigDecimal rate = rateFor(currency, trip, request.getExchangeRate(), expenseDate.toLocalDate());
        return inTransaction(() -> insertExpense(trip, request, currency, rate, expenseDate, userId));
    }

    private ExpenseResponse insertExpense(Trip trip, CreateExpenseRequest request, String currency, BigDecimal rate,
                                          LocalDateTime expenseDate, UUID userId) {
        UUID tripId = trip.getId();
        TripRoster roster = participants.roster(trip);
        ExpenseComposer.Resolver resolver = tripResolver(roster, Set.of());
        BigDecimal amount = request.getAmount();

        ParticipantRef legacyPayer = request.getPayers() != null && !request.getPayers().isEmpty() ? null
                : request.getPaidByGuestMemberId() != null
                ? resolver.resolve(null, request.getPaidByGuestMemberId(), null, false)
                : resolver.resolve(request.getPaidBy() != null ? request.getPaidBy() : userId, null, null, false);
        List<PayerLine> payers = ExpenseComposer.payers(request.getPayers(), legacyPayer, amount, resolver);
        ExpenseComposer.Shares shares = ExpenseComposer.shares(request.getSplitMethod(), amount, currency,
                request.getSplits(), resolver);
        ExpenseComposition composition = new ExpenseComposition(shares.storedMethod(), payers, shares.splits());

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(request.getActivityId())
                .amount(amount)
                .currency(currency)
                .category(request.getCategory())
                .description(request.getDescription())
                .expenseDate(expenseDate)
                .splitMethod(shares.storedMethod())
                .createdBy(userId)
                .build();
        ParticipantRef primary = composition.primaryPayer();
        ExpenseLedgerStore.applyPrimaryPayer(expense, primary, roster.guestName(primary));
        applyRate(expense, rate);

        expenseRepository.insert(expense);
        syncExpensePhotos(expense, request.getPhotoUrls(), userId);
        ledgerStore.write(expense, composition, List.of(), null);

        log.info("Expense created: {} in trip: {}", expense.getId(), tripId);

        notificationHelper.emitGeneric(tripId, userId, NotificationType.EXPENSE_ADDED,
                expenseNotificationData(expense, trip, userId, "/trip/" + tripId + "/expenses/" + expense.getId()),
                expenseRecipients(expense), null);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_CREATED, tripId, expense.getId(), userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, expense.getId(), userId);

        return mapToExpenseResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(UUID tripId, ExpenseCategory category, UUID userId) {
        return getExpenses(tripId, category, null, null, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(UUID tripId, ExpenseCategory category, Integer page, Integer size,
                                             UUID userId) {
        validateTripAccess(tripId, userId);
        List<Expense> expenses;
        if (page == null && size == null) {
            expenses = expenseRepository.findByTripId(tripId);
            if (category != null) {
                expenses = expenses.stream()
                        .filter(e -> e.getCategory() == category)
                        .collect(Collectors.toList());
            }
        } else {
            int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size == null ? DEFAULT_PAGE_SIZE : size));
            int pageNumber = Math.max(0, page == null ? 0 : page);
            expenses = expenseRepository.findByTripIdPage(tripId, category, pageSize, pageNumber * pageSize);
        }

        // One media query for the whole page rather than one per expense.
        Map<UUID, List<MediaAsset>> photosByExpense = mediaAssetRepository
                .findByEntityIds(EXPENSE_ENTITY_TYPE,
                        expenses.stream().map(Expense::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(MediaAsset::getEntityId));

        ExpenseBatch batch = loadExpenseBatch(expenses);
        return expenses.stream()
                .map(expense -> mapToExpenseResponse(
                        expense,
                        photosByExpense.getOrDefault(expense.getId(), List.of()),
                        batch))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetOverviewResponse getBudgetOverview(UUID tripId, UUID userId) {
        validateTripAccess(tripId, userId);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        String currency = tripCurrencyOf(trip);
        List<Expense> expenses = expenseRepository.findByTripId(tripId);

        BigDecimal totalSpent = expenses.stream()
                .map(e -> resolveAmountInTripCurrency(e, trip))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEstimated = activityRepository.findByTripId(tripId).stream()
                .map(a -> resolveEstimatedCostInTripCurrency(a, trip))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = trip.getBudget() != null ? trip.getBudget() : BigDecimal.ZERO;
        BigDecimal remaining = totalBudget.subtract(totalSpent);

        int percentageSpent = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.multiply(new BigDecimal(100))
                        .divide(totalBudget, 0, RoundingMode.HALF_UP)
                        .intValue()
                : 0;

        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Expense expense : expenses) {
            String cat = expense.getCategory().toString();
            BigDecimal amount = resolveAmountInTripCurrency(expense, trip);
            byCategory.put(cat, byCategory.getOrDefault(cat, BigDecimal.ZERO).add(amount));
        }

        BigDecimal myShare = BigDecimal.ZERO;
        BigDecimal myPaid = BigDecimal.ZERO;
        Map<UUID, List<ExpenseSplit>> splitsByExpense = expenseSplitRepository
                .findByExpenseIds(expenses.stream().map(Expense::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));
        for (Expense expense : expenses) {
            for (ExpenseSplit split : splitsByExpense.getOrDefault(expense.getId(), List.of())) {
                if (split.getUserId() == null || !split.getUserId().equals(userId)) {
                    continue;
                }
                BigDecimal splitInTripCurrency = resolveSplitAmountInTripCurrency(expense, split.getAmount(), trip);
                myShare = myShare.add(splitInTripCurrency);
                boolean userPaid = expense.getPaidBy() != null && expense.getPaidBy().equals(userId);
                if (userPaid || Boolean.TRUE.equals(split.getIsSettled())) {
                    myPaid = myPaid.add(splitInTripCurrency);
                }
            }
        }

        return BudgetOverviewResponse.builder()
                .totalBudget(totalBudget)
                .totalEstimated(totalEstimated)
                .totalSpent(totalSpent)
                .remaining(remaining)
                .percentageSpent(percentageSpent)
                .currency(currency)
                .myShare(myShare.compareTo(BigDecimal.ZERO) > 0 ? myShare : null)
                .myPaid(myPaid.compareTo(BigDecimal.ZERO) > 0 ? myPaid : null)
                .byCategory(byCategory)
                .build();
    }

    @Override
    @Transactional
    public void deleteExpense(UUID tripId, UUID expenseId, UUID userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        if (!tripId.equals(expense.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        validateTripEditAccess(tripId, userId);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        Set<UUID> recipients = expenseRecipients(expense);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        imageStorageCleanupService.deleteImagesForEntityRecord("EXPENSE", expenseId);
        mediaAssetRepository.softDeleteByEntity(EXPENSE_ENTITY_TYPE, expenseId);
        ledgerStore.beforeDelete(splits);
        expenseSplitRepository.deleteByExpenseId(expenseId);
        // Payers go with the row (ON DELETE CASCADE, V268).
        expenseRepository.deleteById(expenseId);
        log.info("Expense deleted: {}", expenseId);

        notificationHelper.emitGeneric(tripId, userId, NotificationType.EXPENSE_DELETED,
                expenseNotificationData(expense, trip, userId, "/trip/" + tripId + "/expenses"),
                recipients, null);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_DELETED, tripId, expenseId, userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, expenseId, userId);
    }

    /** Not {@code @Transactional}, for the same reason as {@link #createExpense}. */
    @Override
    public ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));
        if (!tripId.equals(expense.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }
        Trip trip = validateTripEditAccess(tripId, userId);

        String currency = request.getCurrency() != null ? currencyCode(request.getCurrency()) : expense.getCurrency();
        boolean currencyChanged = currency != null && !currency.equalsIgnoreCase(expense.getCurrency());
        LocalDateTime expenseDate = request.getExpenseDate() != null
                ? resolveExpenseDate(request.getExpenseDate())
                : expense.getExpenseDate() != null ? expense.getExpenseDate() : LocalDateTime.now();
        BigDecimal rate = rateForUpdate(expense, trip, currency, currencyChanged, request.getExchangeRate(),
                expenseDate.toLocalDate());
        return inTransaction(() -> applyUpdate(expense, trip, request, currency, rate, userId));
    }

    private ExpenseResponse applyUpdate(Expense expense, Trip trip, UpdateExpenseRequest request, String currency,
                                        BigDecimal rate, UUID userId) {
        UUID tripId = trip.getId();
        UUID expenseId = expense.getId();
        Set<UUID> affectedRecipients = expenseRecipients(expense);
        List<ExpenseSplit> previousSplits = expenseSplitRepository.findByExpenseId(expenseId);
        ExpenseLedgerStore.Details details = ledgerStore.details(List.of(expense));
        List<PayerLine> previousPayers = details.payers(expenseId);
        ParticipantRef previousPrimary = ExpenseLedgerStore.primaryPayerOf(expense);
        String previousGuestPayerName = expense.getPaidByGuestName();

        BigDecimal amount = request.getAmount() != null ? request.getAmount() : expense.getAmount();
        boolean amountChanged = amount.compareTo(expense.getAmount()) != 0;

        expense.setAmount(amount);
        expense.setCurrency(currency);
        if (request.getCategory() != null) {
            expense.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            expense.setDescription(request.getDescription());
        }
        if (request.getExpenseDate() != null) {
            expense.setExpenseDate(resolveExpenseDate(request.getExpenseDate()));
        }
        if (request.getActivityId() != null) {
            expense.setActivityId(request.getActivityId());
        }
        if (request.getPhotoUrls() != null) {
            syncExpensePhotos(expense, request.getPhotoUrls(), userId);
        }
        applyRate(expense, rate);

        TripRoster roster = participants.roster(trip);
        ExpenseComposer.Resolver resolver = tripResolver(roster,
                ExpenseLedgerStore.participantsOf(previousPayers, previousSplits));

        List<PayerLine> payers = updatedPayers(request, amount, amountChanged, previousPayers, previousPrimary, resolver);
        ExpenseComposer.Shares shares = updatedShares(expense, request, amount, amountChanged, previousSplits, resolver);
        ExpenseComposition composition = new ExpenseComposition(shares.storedMethod(), payers, shares.splits());

        expense.setSplitMethod(shares.storedMethod());
        ParticipantRef primary = composition.primaryPayer();
        String guestName = roster.guestName(primary);
        if (guestName == null && primary.isGuest() && primary.equals(previousPrimary)) {
            guestName = previousGuestPayerName;
        }
        ExpenseLedgerStore.applyPrimaryPayer(expense, primary, guestName);

        expenseRepository.updateById(expense);
        ledgerStore.write(expense, composition, previousSplits, previousPrimary);
        log.info("Expense updated: expenseId={}, payers={}, splits={}", expenseId, payers.size(),
                composition.splits().size());

        affectedRecipients.addAll(expenseRecipients(expense));
        notificationHelper.emitGeneric(tripId, userId, NotificationType.EXPENSE_UPDATED,
                expenseNotificationData(expense, trip, userId, "/trip/" + tripId + "/expenses/" + expenseId),
                affectedRecipients, null);
        boolean splitsTouched = request.getSplits() != null || request.getSplitWith() != null
                || request.getSplitMethod() != null || request.getPayers() != null;
        tripRealtimePublisher.publishAfterCommit(
                splitsTouched ? TripRealtimeEventType.EXPENSE_SPLITS_UPDATED : TripRealtimeEventType.EXPENSE_UPDATED,
                tripId,
                expenseId,
                userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, expenseId, userId);

        return mapToExpenseResponse(expense);
    }

    /**
     * The new payers: the {@code payers} list, else a legacy {@code paidById} /
     * {@code paidByGuestMemberId} for the whole amount, else unchanged while the amount is (an old
     * build editing a several-payer expense keeps it intact), else the main payer for the new amount.
     */
    private List<PayerLine> updatedPayers(UpdateExpenseRequest request, BigDecimal amount, boolean amountChanged,
                                          List<PayerLine> previousPayers, ParticipantRef previousPrimary,
                                          ExpenseComposer.Resolver resolver) {
        if (request.getPayers() != null && !request.getPayers().isEmpty()) {
            return ExpenseComposer.payers(request.getPayers(), null, amount, resolver);
        }
        if (request.getPaidById() != null) {
            return List.of(new PayerLine(resolver.resolve(request.getPaidById(), null, null, false), amount));
        }
        if (request.getPaidByGuestMemberId() != null) {
            return List.of(new PayerLine(resolver.resolve(null, request.getPaidByGuestMemberId(), null, false), amount));
        }
        if (!amountChanged && !previousPayers.isEmpty()) {
            return previousPayers;
        }
        ParticipantRef main = previousPayers.isEmpty() ? previousPrimary
                : new ExpenseComposition(null, previousPayers, List.of()).primaryPayer();
        if (main == null) throw invalid("Choose who paid");
        return List.of(new PayerLine(main, amount));
    }

    /**
     * The new shares. A {@code splitMethod} recomputes from the request; the legacy shapes keep
     * working ({@code splitType=equal} over {@code splitWith}, or the split amounts as sent); a new
     * amount with no new split keeps the split's proportions; otherwise the shares stay as they are.
     */
    private ExpenseComposer.Shares updatedShares(Expense expense, UpdateExpenseRequest request, BigDecimal amount,
                                                 boolean amountChanged, List<ExpenseSplit> previousSplits,
                                                 ExpenseComposer.Resolver resolver) {
        String currency = expense.getCurrency();
        if (request.getSplitMethod() != null) {
            return ExpenseComposer.shares(request.getSplitMethod(), amount, currency, request.getSplits(), resolver);
        }
        if ("equal".equalsIgnoreCase(request.getSplitType()) && (amountChanged || request.getSplitWith() != null)) {
            List<ExpenseSplitRequest> people = request.getSplitWith() != null
                    ? request.getSplitWith().stream().filter(Objects::nonNull).distinct()
                            .map(id -> ExpenseSplitRequest.builder().userId(id).build()).toList()
                    : asRequests(previousSplits);
            if (people.isEmpty()) return new ExpenseComposer.Shares(SplitMethod.EQUAL, List.of());
            return ExpenseComposer.shares(SplitMethod.EQUAL, amount, currency, people, resolver);
        }
        if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            return ExpenseComposer.shares(null, amount, currency, request.getSplits(), resolver);
        }
        if (amountChanged && !previousSplits.isEmpty()) {
            return rescaled(expense, amount, previousSplits, resolver);
        }
        return new ExpenseComposer.Shares(expense.getSplitMethod(), linesOf(previousSplits));
    }

    /** Same people, same method, new amount: what an old build's amount-only edit means. */
    private ExpenseComposer.Shares rescaled(Expense expense, BigDecimal amount, List<ExpenseSplit> previousSplits,
                                            ExpenseComposer.Resolver resolver) {
        String currency = expense.getCurrency();
        SplitMethod method = expense.getSplitMethod();
        if (method == null && SplitCalculator.isEvenSplit(expense.getAmount(), currency,
                previousSplits.stream().map(ExpenseSplit::getAmount).toList())) {
            method = SplitMethod.EQUAL;
        }
        boolean allIdentified = previousSplits.stream().allMatch(s -> s.getUserId() != null || s.getGuestMemberId() != null);
        if (allIdentified && (method == SplitMethod.EQUAL || method == SplitMethod.PERCENT
                || method == SplitMethod.SHARES || method == SplitMethod.ADJUSTMENT)) {
            return ExpenseComposer.shares(method, amount, currency, asRequests(previousSplits), resolver);
        }
        List<BigDecimal> weights = previousSplits.stream()
                .map(split -> split.getAmount() == null ? BigDecimal.ZERO : split.getAmount()).toList();
        if (weights.stream().allMatch(weight -> weight.signum() == 0)) {
            weights = previousSplits.stream().map(split -> BigDecimal.ONE).toList();
        }
        List<BigDecimal> amounts = SplitCalculator.proportional(amount, currency, weights);
        List<SplitLine> lines = new ArrayList<>();
        for (int i = 0; i < previousSplits.size(); i++) {
            ExpenseSplit split = previousSplits.get(i);
            lines.add(new SplitLine(ParticipantRef.of(split.getUserId(), split.getGuestMemberId()),
                    split.getGuestName(), amounts.get(i), null));
        }
        return new ExpenseComposer.Shares(method, lines);
    }

    private static List<ExpenseSplitRequest> asRequests(List<ExpenseSplit> splits) {
        return splits.stream().map(split -> ExpenseSplitRequest.builder()
                .userId(split.getUserId())
                .guestMemberId(split.getGuestMemberId())
                .guestName(split.getGuestName())
                .amount(split.getAmount())
                .shareInput(split.getShareInput())
                .build()).toList();
    }

    private static List<SplitLine> linesOf(List<ExpenseSplit> splits) {
        return splits.stream().map(split -> new SplitLine(ParticipantRef.of(split.getUserId(), split.getGuestMemberId()),
                split.getGuestName(), split.getAmount(), split.getShareInput())).toList();
    }

    /**
     * People named on a trip expense must belong to the trip (plan §1 bug 7): the owner, an
     * ACCEPTED member, or a guest of this trip. Someone already on the expense stays allowed.
     */
    private ExpenseComposer.Resolver tripResolver(TripRoster roster, Set<ParticipantRef> alreadyOnExpense) {
        return new ExpenseComposer.Resolver() {
            @Override
            public ParticipantRef resolve(UUID userId, UUID guestMemberId, String guestName, boolean legacy) {
                ParticipantRef participant = roster.resolve(userId, guestMemberId);
                if (participant == null) participant = roster.guestNamed(guestName);
                if (participant == null) {
                    // An old build names a guest it has no id for by name only; that share is
                    // kept as it always was, and the ledger leaves it with the payer.
                    if (legacy && guestName != null && !guestName.isBlank()) return null;
                    throw invalid("Each split must have either userId or guestName");
                }
                if (!roster.contains(participant) && !alreadyOnExpense.contains(participant)) {
                    throw invalid("Everyone on an expense must be a member of the trip");
                }
                return participant;
            }

            @Override
            public String guestName(ParticipantRef participant) {
                return roster.guestName(participant);
            }
        };
    }

    private String tripCurrencyOf(Trip trip) {
        return trip.getCurrency() != null && !trip.getCurrency().isBlank()
                ? trip.getCurrency()
                : "VND";
    }

    private static String currencyCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE.matcher(code).matches()) throw invalid("Currency must be a 3-letter code");
        return code;
    }

    /**
     * 1 for the trip's own currency; the rate the app sent; else the provider's rate for the day
     * the money was spent. No rate refuses the expense (plan §7): storing 1:1 priced one JPY as
     * one VND for good (ISSUES A32).
     */
    private BigDecimal rateFor(String expenseCurrency, Trip trip, BigDecimal requested, LocalDate spentOn) {
        String tripCurrency = tripCurrencyOf(trip);
        if (expenseCurrency.equalsIgnoreCase(tripCurrency)) return BigDecimal.ONE;
        if (requested != null && requested.signum() > 0) return requested;
        return exchangeRateService.findRateOn(expenseCurrency, tripCurrency, spentOn)
                .orElseThrow(() -> new BusinessException(ErrorConstant.EXCHANGE_RATE_UNAVAILABLE,
                        "No exchange rate from " + expenseCurrency + " to " + tripCurrency));
    }

    /**
     * On an edit: a new currency is priced afresh, ignoring a rate the app merely sent back
     * unchanged (old builds resend the previous rate after a currency change, plan §1 bug 3);
     * otherwise the stored rate stands, so changing the amount does not re-price at today's rate.
     */
    private BigDecimal rateForUpdate(Expense expense, Trip trip, String currency, boolean currencyChanged,
                                     BigDecimal requested, LocalDate spentOn) {
        String tripCurrency = tripCurrencyOf(trip);
        if (currency.equalsIgnoreCase(tripCurrency)) return BigDecimal.ONE;
        BigDecimal stored = expense.getExchangeRate();
        if (currencyChanged && requested != null && stored != null && requested.compareTo(stored) == 0) {
            requested = null;
        }
        if (requested != null && requested.signum() > 0) return requested;
        boolean storedIsReal = stored != null && stored.signum() > 0 && stored.compareTo(BigDecimal.ONE) != 0;
        if (!currencyChanged && storedIsReal) return stored;
        return rateFor(currency, trip, null, spentOn);
    }

    private static void applyRate(Expense expense, BigDecimal rate) {
        expense.setExchangeRate(rate);
        expense.setAmountInTripCurrency(expense.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    /** Runs {@code work} in a transaction, joining the caller's when there is one. */
    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private Set<UUID> expenseRecipients(Expense expense) {
        Set<UUID> recipients = expenseSplitRepository.findByExpenseId(expense.getId()).stream()
                .map(ExpenseSplit::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (expense.getPaidBy() != null && expense.getPaidByGuestMemberId() == null
                && userRepository.findById(expense.getPaidBy()).isPresent()) {
            recipients.add(expense.getPaidBy());
        }
        return recipients;
    }

    private Map<String, Object> expenseNotificationData(Expense expense, Trip trip, UUID actorId, String deepLink) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorName", notificationHelper.actorName(actorId));
        data.put("expenseName", expense.getDescription());
        data.put("amount", expense.getAmount());
        data.put("currency", expense.getCurrency());
        data.put("tripName", trip.getName());
        data.put("expenseId", expense.getId());
        data.put("deepLink", deepLink);
        return data;
    }

    private BigDecimal resolveAmountInTripCurrency(Expense expense, Trip trip) {
        if (expense.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        String tripCurrency = tripCurrencyOf(trip);
        String expenseCurrency = expense.getCurrency() != null ? expense.getCurrency() : tripCurrency;
        if (expenseCurrency.equalsIgnoreCase(tripCurrency)) {
            return expense.getAmount();
        }
        if (expense.getAmountInTripCurrency() != null
                && expense.getExchangeRate() != null
                && expense.getExchangeRate().compareTo(BigDecimal.ZERO) > 0) {
            return expense.getAmountInTripCurrency();
        }
        // No frozen rate (a currency change found none): today's, or left out rather than 1:1.
        Optional<BigDecimal> rate = exchangeRateService.findRate(expenseCurrency, tripCurrency);
        if (rate.isEmpty()) {
            log.warn("Expense {} has no rate {}->{}; left out of the trip totals", expense.getId(),
                    expenseCurrency, tripCurrency);
            return BigDecimal.ZERO;
        }
        return expense.getAmount().multiply(rate.get()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEstimatedCostInTripCurrency(Activity activity, Trip trip) {
        if (activity.getEstimatedCost() == null
                || activity.getEstimatedCost().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        String tripCurrency = tripCurrencyOf(trip);
        String costCurrency = activity.getCostCurrency() != null && !activity.getCostCurrency().isBlank()
                ? activity.getCostCurrency()
                : tripCurrency;
        return exchangeRateService.convert(activity.getEstimatedCost(), costCurrency, tripCurrency);
    }

    private BigDecimal resolveSplitAmountInTripCurrency(Expense expense, BigDecimal splitAmount, Trip trip) {
        if (splitAmount == null || splitAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amountInTripCurrency = resolveAmountInTripCurrency(expense, trip);
        return splitAmount.multiply(amountInTripCurrency)
                .divide(expense.getAmount(), 2, RoundingMode.HALF_UP);
    }

    /**
     * Everything mapToExpenseResponse needs from other tables, read once for a whole page
     * of expenses. Rendering a hundred expenses used to cost roughly eight hundred queries:
     * a trip and a payer lookup per expense, a splits query per expense, and a user lookup
     * per split. These are the same lookups with the same predicates, including the same
     * "row missing or soft deleted means null" behaviour; only the number of round trips
     * changes.
     */
    private record ExpenseBatch(
            Map<UUID, Trip> trips,
            Map<UUID, TripMember> guestMembers,
            Map<UUID, User> users,
            Map<UUID, List<ExpenseSplit>> splitsByExpense,
            ExpenseLedgerStore.Details details,
            Map<ParticipantRef, WalletPersonResponse> people) {

        Trip trip(UUID tripId) {
            return tripId == null ? null : trips.get(tripId);
        }

        TripMember guestMember(UUID memberId) {
            return memberId == null ? null : guestMembers.get(memberId);
        }

        User user(UUID userId) {
            return userId == null ? null : users.get(userId);
        }

        List<ExpenseSplit> splits(UUID expenseId) {
            return splitsByExpense.getOrDefault(expenseId, List.of());
        }
    }

    private ExpenseBatch loadExpenseBatch(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            return new ExpenseBatch(Map.of(), Map.of(), Map.of(), Map.of(),
                    ledgerStore.details(List.of()), Map.of());
        }

        Map<UUID, Trip> trips = new HashMap<>();
        expenses.stream().map(Expense::getTripId).filter(java.util.Objects::nonNull).distinct()
                .forEach(tripId -> tripRepository.findById(tripId)
                        .ifPresent(trip -> trips.put(tripId, trip)));

        // trip_members owns no batch finder we may touch, but only guest paid expenses
        // reach it and the distinct set is usually a single row.
        Map<UUID, TripMember> guestMembers = new HashMap<>();
        expenses.stream().map(Expense::getPaidByGuestMemberId).filter(java.util.Objects::nonNull).distinct()
                .forEach(memberId -> tripMemberRepository.findById(memberId)
                        .ifPresent(member -> guestMembers.put(memberId, member)));

        // One statement for every split on the page. Neither this nor the per expense
        // query orders its rows, and grouping keeps each expense in the order the scan
        // produced them, so the rendered split order is unchanged.
        Map<UUID, List<ExpenseSplit>> splitsByExpense = new java.util.LinkedHashMap<>();
        for (ExpenseSplit split : expenseSplitRepository.findByExpenseIds(
                expenses.stream().map(Expense::getId).toList())) {
            splitsByExpense.computeIfAbsent(split.getExpenseId(), key -> new java.util.ArrayList<>())
                    .add(split);
        }

        Set<UUID> userIds = new java.util.LinkedHashSet<>();
        expenses.stream()
                .filter(expense -> expense.getPaidByGuestMemberId() == null)
                .map(Expense::getPaidBy).filter(java.util.Objects::nonNull).forEach(userIds::add);
        guestMembers.values().stream().map(TripMember::getUserId)
                .filter(java.util.Objects::nonNull).forEach(userIds::add);
        splitsByExpense.values().stream().flatMap(List::stream).map(ExpenseSplit::getUserId)
                .filter(java.util.Objects::nonNull).forEach(userIds::add);

        // findByIds carries the same deleted_at IS NULL filter findById does, so a soft
        // deleted user is absent here exactly as it was null there.
        Map<UUID, User> users = userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        ExpenseLedgerStore.Details details = ledgerStore.details(expenses);
        Set<ParticipantRef> refs = new LinkedHashSet<>();
        details.payers().values().forEach(lines -> lines.forEach(line -> refs.add(line.participant())));
        refs.remove(null);
        Map<ParticipantRef, WalletPersonResponse> people = refs.isEmpty() ? Map.of() : participants.describe(refs);

        return new ExpenseBatch(trips, guestMembers, users, splitsByExpense, details, people);
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        return mapToExpenseResponse(
                expense,
                mediaAssetRepository.findByEntity(EXPENSE_ENTITY_TYPE, expense.getId()),
                loadExpenseBatch(List.of(expense)));
    }

    /**
     * Brings the expense's media_assets rows in line with its url list.
     *
     * <p>Only the difference is written: a url that already has a row keeps it,
     * and with it whatever capture date and coordinates were read off the file.
     * Rewriting every row on each save would quietly erase that metadata the
     * first time somebody edited an unrelated field.
     */
    private void syncExpensePhotos(Expense expense, List<String> urls, UUID userId) {
        ExpensePhotoSync.sync(mediaAssetRepository, expense, urls, userId);
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense, List<MediaAsset> photos,
                                                 ExpenseBatch batch) {
        Trip trip = batch.trip(expense.getTripId());
        BigDecimal amountInTripCurrency = trip != null
                ? resolveAmountInTripCurrency(expense, trip)
                : expense.getAmount();
        BigDecimal exchangeRate = expense.getExchangeRate();
        if (exchangeRate == null && trip != null && expense.getAmount() != null
                && expense.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            exchangeRate = amountInTripCurrency.divide(expense.getAmount(), 10, RoundingMode.HALF_UP);
        }

        // Handle paidBy - can be registered user or guest
        UserResponse paidByResponse = null;

        if (expense.getPaidByGuestMemberId() != null) {
            TripMember guestMember = batch.guestMember(expense.getPaidByGuestMemberId());
            if (guestMember != null) {
                if (guestMember.getUserId() != null) {
                    // Guest is linked to a user - fetch user info
                    User user = batch.user(guestMember.getUserId());
                    paidByResponse = mapToUserResponse(user);
                } else {
                    // Guest is not linked - use guest name
                    paidByResponse = UserResponse.builder()
                            .id(null)
                            .email(null)
                            .username(guestMember.getGuestName())
                            .fullName(guestMember.getGuestName())
                            .avatarUrl(null)
                            .build();
                }
            } else {
                log.warn("Guest payer {} of expense {} not found", expense.getPaidByGuestMemberId(), expense.getId());
            }
        } else if (expense.getPaidBy() != null) {
            User paidBy = batch.user(expense.getPaidBy());
            paidByResponse = mapToUserResponse(paidBy);
        }

        List<ExpenseSplit> splits = batch.splits(expense.getId());

        List<ExpenseSplitResponse> splitResponses = splits.stream()
                .map(s -> {
                    // Handle guest members (userId can be null)
                    UserResponse userResponse = null;
                    if (s.getUserId() != null) {
                        User user = batch.user(s.getUserId());
                        if (user != null) {
                            userResponse = UserResponse.builder()
                                    .id(user.getId())
                                    .email(user.getEmail())
                                    .username(user.getUsername())
                                    .fullName(user.getFullName())
                                    .avatarUrl(user.getAvatarUrl())
                                    .build();
                        }
                    } else if (s.getGuestName() != null) {
                        // Guest member - create UserResponse with guest info
                        userResponse = UserResponse.builder()
                                .id(null) // Guest has no userId
                                .email(null)
                                .username(s.getGuestName())
                                .fullName(s.getGuestName())
                                .avatarUrl(null)
                                .build();
                    }
                    return ExpenseSplitResponse.builder()
                            .id(s.getId())
                            .user(userResponse)
                            .guestMemberId(s.getGuestMemberId())
                            .amount(s.getAmount())
                            .isPaid(s.getIsSettled())
                            .shareInput(s.getShareInput())
                            .build();
                })
                .collect(Collectors.toList());

        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(
                photos.stream()
                        .filter(asset -> asset.getAssetRole() == null
                                || "PHOTO".equalsIgnoreCase(asset.getAssetRole()))
                        .toList());

        ExpenseLedgerStore.Details details = batch.details();
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .exchangeRate(exchangeRate)
                .amountInTripCurrency(amountInTripCurrency)
                .category(expense.getCategory().toString())
                .description(expense.getDescription())
                .activityId(expense.getActivityId())
                .paidBy(paidByResponse)
                .paidByGuestMemberId(expense.getPaidByGuestMemberId())
                .splits(splitResponses)
                .photoUrls(expensePhotoUrls(expense, photoResponses))
                .photoUrlsV2(photoResponses)
                .expenseDate(expense.getExpenseDate() != null ? expense.getExpenseDate() : expense.getCreatedAt())
                .createdAt(expense.getCreatedAt())
                .payers(details.payers(expense.getId()).stream()
                        .map(payer -> ExpensePayerResponse.builder()
                                .participant(ExpenseParticipants.person(payer.participant(), batch.people()))
                                .amount(payer.amount())
                                .build())
                        .toList())
                .splitMethod(splitMethodOf(expense, splits).name())
                .build();
    }

    /** The stored method, or for a row written without one: EQUAL when the shares are the even split. */
    static SplitMethod splitMethodOf(Expense expense, List<ExpenseSplit> splits) {
        if (expense.getSplitMethod() != null) return expense.getSplitMethod();
        if (splits.isEmpty()) return SplitMethod.EQUAL;
        return SplitCalculator.isEvenSplit(expense.getAmount(), expense.getCurrency(),
                splits.stream().map(ExpenseSplit::getAmount).toList()) ? SplitMethod.EQUAL : SplitMethod.EXACT;
    }

    /**
     * The date the client asked for, or now when it sent none.
     *
     * <p>A day of slack absorbs a phone whose clock or time zone is ahead of the server's;
     * anything past that is not a date anyone spent money on, and letting it through would
     * park the row at the top of every list forever.
     */
    private LocalDateTime resolveExpenseDate(LocalDateTime requested) {
        if (requested == null) {
            return LocalDateTime.now();
        }
        if (requested.isAfter(LocalDateTime.now().plusDays(1))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Expense date cannot be in the future");
        }
        return requested;
    }

    private List<String> expensePhotoUrls(Expense expense, List<MemoryImageResponse> photos) {
        return MediaAssetResponseMapper.toUrls(photos);
    }

    private com.ds.goroute.dto.response.UserResponse mapToUserResponse(User user) {
        if (user == null) return null;
        return com.ds.goroute.dto.response.UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .defaultCurrency(user.getDefaultCurrency())
                .language(user.getLanguage())
                .build();
    }

    /**
     * An old build's "paid" mark on one share. It now also records (or soft-deletes) the
     * settlement that share stands for, so the new balances follow (epic 05 §2.4).
     */
    @Override
    @Transactional
    public ExpenseSplitResponse markPaymentForSplit(UUID tripId, UUID expenseId, UUID splitId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking payment for split: splitId={}, expenseId={}, tripId={}, userId={}", splitId, expenseId, tripId, userId);

        // Settling is a participant action, not a budget edit: an accepted viewer may
        // update only their own split, while the authorization below still lets the
        // registered payer reconcile splits they funded.
        validateTripAccess(tripId, userId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        if (!tripId.equals(expense.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        ExpenseSplit split = expenseSplitRepository.findById(splitId);
        if (split == null || !split.getExpenseId().equals(expenseId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Split not found");
        }

        boolean isExpensePayer = expense.getPaidByGuestMemberId() == null
                && expense.getPaidBy() != null && expense.getPaidBy().equals(userId);
        boolean isOwnSplit = split.getUserId() != null && split.getUserId().equals(userId);
        if (!isExpensePayer && !isOwnSplit) {
            throw new BusinessException(
                    ErrorConstant.FORBIDDEN_ERROR,
                    "Only the payer or split owner can update this payment");
        }

        boolean paid = !Boolean.FALSE.equals(request.getIsPaid());
        ledgerStore.settleLegacy(expense, List.of(split), paid, userId);

        log.info("Payment marked for split {}: isPaid={}", splitId, paid);

        notificationHelper.emitPaymentMarked(tripId, expenseId, splitId, split,
            expense.getDescription(), expense.getCurrency(), paid, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_PAYMENT_UPDATED, tripId, expenseId, userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, expenseId, userId);

        return mapToExpenseSplitResponse(split);
    }

    @Override
    @Transactional
    public ExpenseResponse markAllPaymentsForExpense(UUID tripId, UUID expenseId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking all payments for expense: expenseId={}, tripId={}, userId={}", expenseId, tripId, userId);

        validateTripEditAccess(tripId, userId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        if (!tripId.equals(expense.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        if (expense.getPaidBy() == null || expense.getPaidByGuestMemberId() != null
                || !expense.getPaidBy().equals(userId)) {
            throw new BusinessException(
                    ErrorConstant.FORBIDDEN_ERROR,
                    "Only the expense payer can update all payments");
        }

        boolean paid = !Boolean.FALSE.equals(request.getIsPaid());
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        ledgerStore.settleLegacy(expense, splits, paid, userId);

        log.info("All payments marked for expense {}: isPaid={}, count={}", expenseId, paid, splits.size());

        notificationHelper.emitPaymentAllMarked(tripId, expenseId, expense.getDescription(), paid, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_PAYMENT_UPDATED, tripId, expenseId, userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, expenseId, userId);

        return mapToExpenseResponse(expense);
    }

    /**
     * Re-prices every expense in the trip's (new) currency at the rate of the day each was spent
     * (plan §7), not today's. Inside a transaction this waits for it to commit, then fetches the
     * rates holding no connection and writes in a transaction of its own. An expense with no rate
     * keeps no converted amount (it is left out of the totals) rather than a 1:1 guess.
     */
    @Override
    public void recalculateForTripCurrency(UUID tripId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recalculateNow(tripId);
                }
            });
            return;
        }
        recalculateNow(tripId);
    }

    private void recalculateNow(UUID tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) return;
        String tripCurrency = tripCurrencyOf(trip);
        List<Expense> expenses = expenseRepository.findByTripId(tripId);
        Map<String, Optional<BigDecimal>> rates = new HashMap<>();
        int unpriced = 0;
        for (Expense expense : expenses) {
            String currency = expense.getCurrency() == null ? tripCurrency : expense.getCurrency();
            LocalDate day = (expense.getExpenseDate() != null ? expense.getExpenseDate()
                    : expense.getCreatedAt() != null ? expense.getCreatedAt() : LocalDateTime.now()).toLocalDate();
            Optional<BigDecimal> rate = currency.equalsIgnoreCase(tripCurrency) ? Optional.of(BigDecimal.ONE)
                    : rates.computeIfAbsent(currency + "|" + day,
                            key -> exchangeRateService.findRateOn(currency, tripCurrency, day));
            if (rate.isPresent()) {
                applyRate(expense, rate.get());
            } else {
                expense.setExchangeRate(null);
                expense.setAmountInTripCurrency(null);
                unpriced++;
            }
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            expenses.forEach(expenseRepository::updateById);
            tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.EXPENSE_UPDATED, tripId, null);
        });
        log.info("Recalculated amountInTripCurrency for {} expenses on trip {} ({}), {} without a rate",
                expenses.size(), tripId, tripCurrency, unpriced);
    }

    /**
     * Old builds' "everything in this trip is paid / not paid". It now touches only the expenses
     * the caller paid (plan §1 bug 5: any editor could settle other people's debts), and a
     * payer's own share always stays paid.
     */
    @Override
    @Transactional
    public void markAllPaymentsForTrip(UUID tripId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking all payments for trip: tripId={}, userId={}", tripId, userId);

        validateTripAccess(tripId, userId);
        boolean paid = !Boolean.FALSE.equals(request.getIsPaid());

        List<Expense> mine = expenseRepository.findByTripId(tripId).stream()
                .filter(expense -> expense.getPaidByGuestMemberId() == null && userId.equals(expense.getPaidBy()))
                .toList();
        Map<UUID, List<ExpenseSplit>> splitsByExpense = expenseSplitRepository
                .findByExpenseIds(mine.stream().map(Expense::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));

        int totalUpdated = 0;
        for (Expense expense : mine) {
            List<ExpenseSplit> splits = splitsByExpense.getOrDefault(expense.getId(), List.of());
            ledgerStore.settleLegacy(expense, splits, paid, userId);
            totalUpdated += splits.size();
        }

        log.info("All payments marked for trip {}: isPaid={}, totalUpdated={}", tripId, paid, totalUpdated);

        notificationHelper.emitPaymentTripMarked(tripId, paid, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.EXPENSE_PAYMENT_UPDATED, tripId, null, userId);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, null, userId);
    }

    private ExpenseSplitResponse mapToExpenseSplitResponse(ExpenseSplit split) {
        UserResponse userResponse = null;
        if (split.getUserId() != null) {
            User user = userRepository.findById(split.getUserId()).orElse(null);
            if (user != null) {
                userResponse = UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build();
            }
        } else if (split.getGuestName() != null) {
            userResponse = UserResponse.builder()
                    .id(null)
                    .email(null)
                    .username(split.getGuestName())
                    .fullName(split.getGuestName())
                    .avatarUrl(null)
                    .build();
        }

        return ExpenseSplitResponse.builder()
                .id(split.getId())
                .user(userResponse)
                .guestMemberId(split.getGuestMemberId())
                .amount(split.getAmount())
                .isPaid(split.getIsSettled())
                .shareInput(split.getShareInput())
                .build();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
