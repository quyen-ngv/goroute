package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final NotificationHelper notificationHelper;
    private final ExchangeRateService exchangeRateService;
    private final ImageStorageCleanupService imageStorageCleanupService;

    /**
     * Check if user has access to trip (must be owner or ACCEPTED member, not LEFT)
     */
    private void validateTripAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        boolean hasAccess = trip.getOwnerId().equals(userId) ||
                           (member.isPresent() && member.get().getStatus() == MemberStatus .ACCEPTED);

        if (!hasAccess) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }
    }

    @Override
    @Transactional
    public ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId) {
        validateTripAccess(tripId, userId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        UUID paidBy = request.getPaidBy() != null ? request.getPaidBy() : userId;

        // If guest payer is provided, fetch guest member info
        String paidByGuestName = request.getPaidByGuestName();
        UUID paidByGuestMemberId = request.getPaidByGuestMemberId();

        if (paidByGuestMemberId != null) {
            TripMember guestMember = tripMemberRepository.findById(paidByGuestMemberId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Guest member not found"));

            // Set paid_by = paidByGuestMemberId (same ID)
            paidBy = paidByGuestMemberId;
            paidByGuestName = guestMember.getGuestName();
        }

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(request.getActivityId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .description(request.getDescription())
                .paidBy(paidBy)
                .paidByGuestName(paidByGuestName)
                .paidByGuestMemberId(paidByGuestMemberId)
                .photoUrls(request.getPhotoUrls() != null ? request.getPhotoUrls().toArray(new String[0]) : null)
                .createdBy(userId)
                .build();

        applyTripCurrencyConversion(expense, trip);

        expenseRepository.insert(expense);

        if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            for (var split : request.getSplits()) {
                if (split.getUserId() == null &&
                    (split.getGuestName() == null || split.getGuestName().trim().isEmpty())) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Each split must have either userId or guestName");
                }

                boolean isSettled = false;
                if (split.getUserId() != null && split.getUserId().equals(paidBy)) {
                    isSettled = true;
                } else if (split.getGuestMemberId() != null && split.getGuestMemberId().equals(paidByGuestMemberId)) {
                    isSettled = true;
                }

                ExpenseSplit expenseSplit = ExpenseSplit.builder()
                        .id(UUID.randomUUID())
                        .expenseId(expense.getId())
                        .userId(split.getUserId())
                        .guestMemberId(split.getGuestMemberId())
                        .guestName(split.getGuestName())
                        .amount(split.getAmount())
                        .isSettled(isSettled)
                        .settledAt(isSettled ? LocalDateTime.now() : null)
                        .build();
                expenseSplitRepository.save(expenseSplit);
            }
        }

        log.info("Expense created: {} in trip: {}", expense.getId(), tripId);

        notificationHelper.emitExpenseCreated(expense, userId);

        return mapToExpenseResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(UUID tripId, String category) {
        List<Expense> expenses = expenseRepository.findByTripId(tripId);

        if (category != null && !category.isEmpty()) {
            final ExpenseCategory categoryEnum = ExpenseCategory.valueOf(category.toUpperCase());
            expenses = expenses.stream()
                    .filter(e -> e.getCategory() == categoryEnum)
                    .collect(Collectors.toList());
        }

        return expenses.stream()
                .map(this::mapToExpenseResponse)
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
        for (Expense expense : expenses) {
            List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expense.getId());
            for (ExpenseSplit split : splits) {
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

        if (!expense.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Allow any trip member to delete expense
        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        imageStorageCleanupService.deleteImagesForEntityRecord("EXPENSE", expenseId);
        expenseSplitRepository.deleteByExpenseId(expenseId);
        expenseRepository.deleteById(expenseId);
        log.info("Expense deleted: {}", expenseId);

        notificationHelper.emitExpenseDeleted(expense, userId);
    }

    @Override
    @Transactional
    public ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId) {
        log.info("ðŸ”µ Updating expense: expenseId={}, tripId={}, userId={}", expenseId, tripId, userId);
        log.info("ðŸ”µ Request paidById: {}, paidByGuestMemberId: {}", request.getPaidById(), request.getPaidByGuestMemberId());

        // 1. Fetch existing expense
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        // 2. Validate expense belongs to trip
        if (!expense.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        // 3. Validate user is member of trip
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Allow any trip member to update expense
        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            log.warn("Unauthorized expense update attempt: expenseId={}, userId={}", expenseId, userId);
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        // Track if amount or currency changed
        boolean amountChanged = request.getAmount() != null && request.getAmount().compareTo(expense.getAmount()) != 0;
        boolean currencyChanged = request.getCurrency() != null && !request.getCurrency().equals(expense.getCurrency());

        // Update expense fields if provided
        if (request.getAmount() != null) {
            expense.setAmount(request.getAmount());
        }
        if (request.getCurrency() != null) {
            expense.setCurrency(request.getCurrency());
        }
        if (request.getCategory() != null) {
            expense.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            expense.setDescription(request.getDescription());
        }
        if (request.getActivityId() != null) {
            expense.setActivityId(request.getActivityId());
        }
        if (request.getPhotoUrls() != null) {
            expense.setPhotoUrls(request.getPhotoUrls().toArray(new String[0]));
        }

        // Update paidBy if provided
        log.info("ðŸ”µ Checking paidBy update: paidById={}, paidByGuestMemberId={}",
                request.getPaidById(), request.getPaidByGuestMemberId());
        log.info("ðŸ”µ Current expense paidBy: {}, paidByGuestMemberId: {}",
                expense.getPaidBy(), expense.getPaidByGuestMemberId());

        if (request.getPaidById() != null) {
            log.info("ðŸŸ¢ Updating paidBy to user: {} (was: {})", request.getPaidById(), expense.getPaidBy());
            expense.setPaidBy(request.getPaidById());
            expense.setPaidByGuestName(null); // Clear guest if setting real user
            expense.setPaidByGuestMemberId(null); // Clear guest member reference
            log.info("ðŸŸ¢ Updated paidBy to user: {}", request.getPaidById());
        } else if (request.getPaidByGuestMemberId() != null) {
            log.info("ðŸŸ¢ Updating paidBy to guest: {}", request.getPaidByGuestMemberId());
            // Fetch guest member to get name
            TripMember guestMember = tripMemberRepository.findById(request.getPaidByGuestMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Guest member not found"));

            // Set paid_by = paidByGuestMemberId (same ID)
            expense.setPaidBy(request.getPaidByGuestMemberId());
            expense.setPaidByGuestMemberId(request.getPaidByGuestMemberId());
            expense.setPaidByGuestName(guestMember.getGuestName());
            log.info("ðŸŸ¢ Updated paidBy to guest: {} (ID: {})",
                    guestMember.getGuestName(), request.getPaidByGuestMemberId());
        } else {
            log.info("âš ï¸ No paidBy update requested (both paidById and paidByGuestMemberId are null)");
        }

        if (currencyChanged || amountChanged) {
            applyTripCurrencyConversion(expense, trip);
            if (request.getExchangeRate() != null) {
                expense.setExchangeRate(request.getExchangeRate());
                expense.setAmountInTripCurrency(
                        expense.getAmount().multiply(request.getExchangeRate())
                                .setScale(2, RoundingMode.HALF_UP));
            }
            log.info("Currency/amount updated for expense {}: {} rate={}",
                    expenseId, expense.getCurrency(), expense.getExchangeRate());
        }

        // 4. If amount changed and splitType=equal â†’ recalculate splits
        if (request.getSplitType() != null && "equal".equalsIgnoreCase(request.getSplitType())) {
            if (amountChanged || request.getSplitWith() != null) {
                // Delete existing splits
                expenseSplitRepository.deleteByExpenseId(expenseId);

                // Get split members
                List<UUID> splitWith = request.getSplitWith();
                if (splitWith != null && !splitWith.isEmpty()) {
                    // Calculate equal split
                    int memberCount = splitWith.size();
                    BigDecimal splitAmount = expense.getAmount().divide(
                            BigDecimal.valueOf(memberCount),
                            2,
                            RoundingMode.DOWN
                    );

                    // Calculate remainder to add to last split
                    BigDecimal totalSplit = splitAmount.multiply(BigDecimal.valueOf(memberCount));
                    BigDecimal remainder = expense.getAmount().subtract(totalSplit);

                    // Create new splits
                    for (int i = 0; i < splitWith.size(); i++) {
                        UUID memberId = splitWith.get(i);
                        BigDecimal amount = splitAmount;

                        // Add remainder to last split
                        if (i == splitWith.size() - 1) {
                            amount = amount.add(remainder);
                        }

                        // Auto-settle if this split is for the payer
                        boolean isSettled = memberId.equals(expense.getPaidBy());

                        ExpenseSplit split = ExpenseSplit.builder()
                                .id(UUID.randomUUID())
                                .expenseId(expenseId)
                                .userId(memberId)
                                .guestMemberId(null) // Equal split is for registered users only
                                .amount(amount)
                                .isSettled(isSettled)
                                .settledAt(isSettled ? LocalDateTime.now() : null)
                                .build();
                        expenseSplitRepository.save(split);
                    }
                    log.info("Recalculated equal splits for expense {}: {} members", expenseId, memberCount);
                }
            }
        } else if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            // Custom splits provided
            // Validate splits sum equals amount
            BigDecimal splitsSum = request.getSplits().stream()
                    .map(s -> s.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (splitsSum.compareTo(expense.getAmount()) != 0) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Split amounts must sum to total expense amount");
            }

            // Delete existing splits and create new ones
            expenseSplitRepository.deleteByExpenseId(expenseId);
            for (var split : request.getSplits()) {
                // Validate: must have either userId or guestName
                if (split.getUserId() == null &&
                    (split.getGuestName() == null || split.getGuestName().trim().isEmpty())) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Each split must have either userId or guestName");
                }

                // Auto-settle if this split is for the payer
                boolean isSettled = false;
                if (split.getUserId() != null && split.getUserId().equals(expense.getPaidBy())) {
                    isSettled = true;
                } else if (split.getGuestMemberId() != null && split.getGuestMemberId().equals(expense.getPaidByGuestMemberId())) {
                    isSettled = true;
                }

                ExpenseSplit expenseSplit = ExpenseSplit.builder()
                        .id(UUID.randomUUID())
                        .expenseId(expenseId)
                        .userId(split.getUserId())
                        .guestMemberId(split.getGuestMemberId())
                        .guestName(split.getGuestName())
                        .amount(split.getAmount())
                        .isSettled(isSettled)
                        .settledAt(isSettled ? LocalDateTime.now() : null)
                        .build();
                expenseSplitRepository.save(expenseSplit);
            }
            log.info("Updated custom splits for expense {}", expenseId);
        }

        log.info("ðŸ”µ Before save - expense paidBy: {}, paidByGuestMemberId: {}",
                expense.getPaidBy(), expense.getPaidByGuestMemberId());

        expenseRepository.updateById(expense);

        log.info("ðŸ”µ After save - expense paidBy: {}, paidByGuestMemberId: {}",
                expense.getPaidBy(), expense.getPaidByGuestMemberId());

        log.info("Cache invalidated for expenses:{}:overview", tripId);

        log.info("ðŸŸ¢ Expense updated successfully: expenseId={}", expenseId);

        notificationHelper.emitExpenseUpdated(expense, userId);

        return mapToExpenseResponse(expense);
    }

    private String tripCurrencyOf(Trip trip) {
        return trip.getCurrency() != null && !trip.getCurrency().isBlank()
                ? trip.getCurrency()
                : "VND";
    }

    private void applyTripCurrencyConversion(Expense expense, Trip trip) {
        String tripCurrency = tripCurrencyOf(trip);
        String expenseCurrency = expense.getCurrency() != null ? expense.getCurrency() : tripCurrency;
        BigDecimal rate = exchangeRateService.getRate(expenseCurrency, tripCurrency);
        expense.setExchangeRate(rate);
        expense.setAmountInTripCurrency(
                expense.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
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
        return exchangeRateService.convert(expense.getAmount(), expenseCurrency, tripCurrency);
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

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        log.info("ðŸ”µ mapToExpenseResponse: expenseId={}, paidBy={}, paidByGuestMemberId={}",
                expense.getId(), expense.getPaidBy(), expense.getPaidByGuestMemberId());

        Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
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

        // Check if this is a guest payer
        if (expense.getPaidByGuestMemberId() != null) {
            log.info("   Guest payer detected: {}", expense.getPaidByGuestMemberId());
            // Guest payer - fetch from trip_members
            TripMember guestMember = tripMemberRepository.findById(expense.getPaidByGuestMemberId()).orElse(null);
            if (guestMember != null) {
                log.info("   Found guest member: {}", guestMember.getGuestName());
                if (guestMember.getUserId() != null) {
                    // Guest is linked to a user - fetch user info
                    User user = userRepository.findById(guestMember.getUserId()).orElse(null);
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
                log.warn("   Guest member not found!");
            }
        } else if (expense.getPaidBy() != null) {
            log.info("   Regular user payer: {}", expense.getPaidBy());
            // Regular user payer
            User paidBy = userRepository.findById(expense.getPaidBy()).orElse(null);
            paidByResponse = mapToUserResponse(paidBy);
        }

        log.info("   Final paidByResponse: {}", paidByResponse != null ? paidByResponse.getFullName() : "null");

        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expense.getId());

        List<ExpenseSplitResponse> splitResponses = splits.stream()
                .map(s -> {
                    // Handle guest members (userId can be null)
                    UserResponse userResponse = null;
                    if (s.getUserId() != null) {
                        User user = userRepository.findById(s.getUserId()).orElse(null);
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
                            .amount(s.getAmount())
                            .isPaid(s.getIsSettled())
                            .build();
                })
                .collect(Collectors.toList());

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
                .photoUrls(expense.getPhotoUrls() != null ? List.of(expense.getPhotoUrls()) : List.of())
                .createdAt(expense.getCreatedAt())
                .build();
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

    @Override
    @Transactional
    public ExpenseSplitResponse markPaymentForSplit(UUID tripId, UUID expenseId, UUID splitId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking payment for split: splitId={}, expenseId={}, tripId={}, userId={}", splitId, expenseId, tripId, userId);

        // 1. Validate user is member of trip
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        // 2. Fetch expense
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        if (!expense.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        // 3. Fetch split
        ExpenseSplit split = expenseSplitRepository.findById(splitId);
        if (split == null || !split.getExpenseId().equals(expenseId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Split not found");
        }

        // 4. Validate permission: only payer or payee can mark as paid
        boolean isExpensePayer = expense.getPaidBy() != null && expense.getPaidBy().equals(userId);
        boolean isExpensePayee = split.getUserId() != null && split.getUserId().equals(userId);
        boolean isGuestPayee = split.getGuestMemberId() != null || (split.getUserId() == null && split.getGuestName() != null);

        // For guest, check if current user is payer, trip owner, or expense creator
        if (isGuestPayee) {
            if (!isExpensePayer && !trip.getOwnerId().equals(userId) && !expense.getCreatedBy().equals(userId)) {
                throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only payer, trip owner, or expense creator can mark guest payment");
            }
        } else if (!isExpensePayer && !isExpensePayee) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only payer or payee can mark payment");
        }

        split.setIsSettled(request.getIsPaid());
        split.setSettledAt(request.getIsPaid() ? LocalDateTime.now() : null);
        expenseSplitRepository.update(split);

        log.info("Payment marked for split {}: isPaid={}", splitId, request.getIsPaid());

        notificationHelper.emitPaymentMarked(tripId, expenseId, splitId, split,
            expense.getDescription(), expense.getCurrency(), request.getIsPaid(), userId);

        return mapToExpenseSplitResponse(split);
    }

    @Override
    @Transactional
    public ExpenseResponse markAllPaymentsForExpense(UUID tripId, UUID expenseId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking all payments for expense: expenseId={}, tripId={}, userId={}", expenseId, tripId, userId);

        // 1. Validate user is member of trip
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        // 2. Fetch expense
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));

        if (!expense.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }

        // 3. Validate permission: only payer or trip owner can mark all as paid

        boolean isExpensePayer = expense.getPaidBy() != null && expense.getPaidBy().equals(userId);
        boolean isTripOwner = trip.getOwnerId().equals(userId);

        if (!isExpensePayer && !isTripOwner) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only payer or trip owner can mark all payments");
        }

        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        for (ExpenseSplit split : splits) {
            split.setIsSettled(request.getIsPaid());
            split.setSettledAt(request.getIsPaid() ? LocalDateTime.now() : null);
            expenseSplitRepository.update(split);
        }

        log.info("All payments marked for expense {}: isPaid={}, count={}", expenseId, request.getIsPaid(), splits.size());

        notificationHelper.emitPaymentAllMarked(tripId, expenseId, expense.getDescription(), request.getIsPaid(), userId);

        return mapToExpenseResponse(expense);
    }

    @Override
    @Transactional
    public void recalculateForTripCurrency(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        List<Expense> expenses = expenseRepository.findByTripId(tripId);
        for (Expense expense : expenses) {
            applyTripCurrencyConversion(expense, trip);
            expenseRepository.updateById(expense);
        }
        log.info("Recalculated amountInTripCurrency for {} expenses on trip {} ({})",
                expenses.size(), tripId, tripCurrencyOf(trip));
    }

    @Override
    @Transactional
    public void markAllPaymentsForTrip(UUID tripId, MarkPaymentRequest request, UUID userId) {
        log.info("Marking all payments for trip: tripId={}, userId={}", tripId, userId);

        // 1. Validate trip exists and user is owner
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only trip owner can mark all payments for trip");
        }

        // 2. Get all expenses for trip
        List<Expense> expenses = expenseRepository.findByTripId(tripId);

        int totalUpdated = 0;
        for (Expense expense : expenses) {
            List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expense.getId());
            for (ExpenseSplit split : splits) {
                split.setIsSettled(request.getIsPaid());
                split.setSettledAt(request.getIsPaid() ? LocalDateTime.now() : null);
                expenseSplitRepository.update(split);
                totalUpdated++;
            }
        }

        log.info("All payments marked for trip {}: isPaid={}, totalUpdated={}", tripId, request.getIsPaid(), totalUpdated);

        notificationHelper.emitPaymentTripMarked(tripId, request.getIsPaid(), userId);
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
                .amount(split.getAmount())
                .isPaid(split.getIsSettled())
                .build();
    }
}
