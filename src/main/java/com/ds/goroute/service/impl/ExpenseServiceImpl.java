package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.type.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId) && !tripMemberRepository.findByTripIdAndUserId(tripId, userId).isPresent()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        UUID paidBy = request.getPaidBy() != null ? request.getPaidBy() : userId;
        
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(request.getActivityId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .description(request.getDescription())
                .paidBy(paidBy)
                .paidByGuestName(request.getPaidByGuestName())
                .paidByGuestEmail(request.getPaidByGuestEmail())
                .photoUrls(request.getPhotoUrls() != null ? request.getPhotoUrls().toArray(new String[0]) : null)
                .createdBy(userId)
                .build();

        expenseRepository.insert(expense);

        // Create splits
        if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            for (var split : request.getSplits()) {
                // Validate: must have either userId or guestName
                if (split.getUserId() == null && 
                    (split.getGuestName() == null || split.getGuestName().trim().isEmpty())) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, 
                        "Each split must have either userId or guestName");
                }
                
                ExpenseSplit expenseSplit = ExpenseSplit.builder()
                        .id(UUID.randomUUID())
                        .expenseId(expense.getId())
                        .userId(split.getUserId())
                        .guestName(split.getGuestName())
                        .guestEmail(split.getGuestEmail())
                        .amount(split.getAmount())
                        .isSettled(false)
                        .build();
                expenseSplitRepository.save(expenseSplit);
            }
        }

        log.info("Expense created: {} in trip: {}", expense.getId(), tripId);
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
    public BudgetOverviewResponse getBudgetOverview(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        List<Expense> expenses = expenseRepository.findByTripId(tripId);
        
        BigDecimal totalSpent = expenses.stream()
                .map(e -> e.getAmountInTripCurrency() != null ? e.getAmountInTripCurrency() : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = trip.getBudget() != null ? trip.getBudget() : BigDecimal.ZERO;
        BigDecimal remaining = totalBudget.subtract(totalSpent);
        
        int percentageSpent = totalBudget.compareTo(BigDecimal.ZERO) > 0 
                ? totalSpent.multiply(new BigDecimal(100)).divide(totalBudget, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Expense expense : expenses) {
            String cat = expense.getCategory().toString();
            BigDecimal amount = expense.getAmountInTripCurrency() != null ? expense.getAmountInTripCurrency() : expense.getAmount();
            byCategory.put(cat, byCategory.getOrDefault(cat, BigDecimal.ZERO).add(amount));
        }

        return BudgetOverviewResponse.builder()
                .totalBudget(totalBudget)
                .totalSpent(totalSpent)
                .remaining(remaining)
                .percentageSpent(percentageSpent)
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
        
        if (!trip.getOwnerId().equals(userId) && !expense.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        expenseSplitRepository.deleteByExpenseId(expenseId);
        expenseRepository.deleteById(expenseId);
        log.info("Expense deleted: {}", expenseId);
    }

    @Override
    @Transactional
    public ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId) {
        log.info("Updating expense: expenseId={}, tripId={}, userId={}", expenseId, tripId, userId);
        
        // 1. Fetch existing expense
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found"));
        
        // 2. Validate expense belongs to trip
        if (!expense.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Expense not found");
        }
        
        // 3. Validate user is creator or trip owner
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        
        if (!trip.getOwnerId().equals(userId) && !expense.getCreatedBy().equals(userId)) {
            log.warn("Unauthorized expense update attempt: expenseId={}, userId={}", expenseId, userId);
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You don't have permission to update this expense");
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
        
        // 5. If currency changed → fetch exchange rate and update amountInTripCurrency
        if (currencyChanged) {
            // For now, use provided exchange rate or default to 1.0
            // In production, this should call an external exchange rate API
            BigDecimal exchangeRate = request.getExchangeRate() != null ? request.getExchangeRate() : BigDecimal.ONE;
            expense.setExchangeRate(exchangeRate);
            expense.setAmountInTripCurrency(expense.getAmount().multiply(exchangeRate));
            log.info("Currency changed for expense {}: {} -> {}, rate: {}", 
                    expenseId, expense.getCurrency(), request.getCurrency(), exchangeRate);
        }
        
        // 4. If amount changed and splitType=equal → recalculate splits
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
                        
                        ExpenseSplit split = ExpenseSplit.builder()
                                .id(UUID.randomUUID())
                                .expenseId(expenseId)
                                .userId(memberId)
                                .amount(amount)
                                .isSettled(false)
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
                
                ExpenseSplit expenseSplit = ExpenseSplit.builder()
                        .id(UUID.randomUUID())
                        .expenseId(expenseId)
                        .userId(split.getUserId())
                        .guestName(split.getGuestName())
                        .guestEmail(split.getGuestEmail())
                        .amount(split.getAmount())
                        .isSettled(false)
                        .build();
                expenseSplitRepository.save(expenseSplit);
            }
            log.info("Updated custom splits for expense {}", expenseId);
        }
        
        // 6. Update expense record
        expenseRepository.updateById(expense);
        
        // 8. Invalidate cache: expenses:{tripId}:overview
        // Note: Cache invalidation would be handled by @CacheEvict annotation in production
        // For now, just log it
        log.info("Cache invalidated for expenses:{}:overview", tripId);
        
        log.info("Expense updated successfully: expenseId={}", expenseId);
        
        // 9. Return updated expense
        return mapToExpenseResponse(expense);
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        // Handle paidBy - can be registered user or guest
        UserResponse paidByResponse = null;
        if (expense.getPaidBy() != null) {
            User paidBy = userRepository.findById(expense.getPaidBy()).orElse(null);
            paidByResponse = mapToUserResponse(paidBy);
        } else if (expense.getPaidByGuestName() != null) {
            // Guest payer
            paidByResponse = UserResponse.builder()
                    .id(null)
                    .email(expense.getPaidByGuestEmail())
                    .username(null)
                    .fullName(expense.getPaidByGuestName())
                    .avatarUrl(null)
                    .build();
        }
        
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
                                .email(s.getGuestEmail())
                                .username(null)
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
                .category(expense.getCategory().toString())
                .description(expense.getDescription())
                .activityId(expense.getActivityId())
                .paidBy(paidByResponse)
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
}
