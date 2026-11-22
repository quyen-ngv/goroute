package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.PersonalExpenseRequest;
import com.ds.goroute.dto.response.PersonalExpenseResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
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
import com.ds.goroute.service.assistant.AssistantIdentity;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Expenses outside any trip (epic 05 §2.4c), Splitwise style.
 *
 * <p>The row has {@code trip_id} NULL. Alone, the creator is the payer with one settled split.
 * Split with friends, every participant is a registered user with one split, and the payer's own
 * split is settled at once; the rest are debts to the payer, which the Wallet shows through the
 * same SQL as trip debts. The creator edits and deletes; the payer and the participants can read
 * it and settle their part. Anyone else gets NOT_FOUND, as if it did not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalExpenseService {

    static final String SPLIT_EQUAL = "EQUAL";
    static final String SPLIT_CUSTOM = "CUSTOM";

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final NotificationService notificationService;

    @Transactional
    public PersonalExpenseResponse create(PersonalExpenseRequest request, UUID userId) {
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .createdBy(userId)
                .build();
        apply(expense, request);
        List<ExpenseSplit> splits = splits(expense, request, userId, Map.of());
        expenseRepository.insert(expense);
        splits.forEach(expenseSplitRepository::save);
        ExpensePhotoSync.sync(mediaAssetRepository, expense, request.getPhotoUrls(), userId);
        notifyAdded(expense, splits, Set.of(), userId);
        log.info("Personal expense created: {} with {} splits", expense.getId(), splits.size());
        return toResponse(expense, splits, userId);
    }

    @Transactional(readOnly = true)
    public PersonalExpenseResponse get(UUID expenseId, UUID userId) {
        Expense expense = requireParty(expenseId, userId);
        return toResponse(expense, expenseSplitRepository.findByExpenseId(expenseId), userId);
    }

    @Transactional
    public PersonalExpenseResponse update(UUID expenseId, PersonalExpenseRequest request, UUID userId) {
        Expense expense = requireCreator(expenseId, userId);
        List<ExpenseSplit> previous = expenseSplitRepository.findByExpenseId(expenseId);
        apply(expense, request);
        Map<UUID, ExpenseSplit> previousByUser = previous.stream()
                .filter(split -> split.getUserId() != null)
                .collect(Collectors.toMap(ExpenseSplit::getUserId, Function.identity(), (a, b) -> a));
        List<ExpenseSplit> splits = splits(expense, request, userId, previousByUser);
        expenseRepository.updateById(expense);
        expenseSplitRepository.deleteByExpenseId(expenseId);
        splits.forEach(expenseSplitRepository::save);
        ExpensePhotoSync.sync(mediaAssetRepository, expense, request.getPhotoUrls(), userId);
        notifyAdded(expense, splits, previousByUser.keySet(), userId);
        return toResponse(expense, splits, userId);
    }

    @Transactional
    public void delete(UUID expenseId, UUID userId) {
        requireCreator(expenseId, userId);
        imageStorageCleanupService.deleteImagesForEntityRecord(ExpensePhotoSync.ENTITY_TYPE, expenseId);
        mediaAssetRepository.softDeleteByEntity(ExpensePhotoSync.ENTITY_TYPE, expenseId);
        expenseSplitRepository.deleteByExpenseId(expenseId);
        expenseRepository.deleteById(expenseId);
        log.info("Personal expense deleted: {}", expenseId);
    }

    /**
     * Settles or reopens one share. The payer may do either for anyone; a participant only for
     * their own share, as on trip expenses. The payer's own share is always settled.
     */
    @Transactional
    public PersonalExpenseResponse markSplit(UUID expenseId, UUID splitId, boolean paid, UUID userId) {
        Expense expense = requireParty(expenseId, userId);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        ExpenseSplit split = splits.stream()
                .filter(candidate -> candidate.getId().equals(splitId))
                .findFirst()
                .orElseThrow(() -> notFound("Expense split not found"));
        boolean isPayer = userId.equals(expense.getPaidBy());
        if (!isPayer && !userId.equals(split.getUserId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the payer or this person can settle this share", HttpStatus.FORBIDDEN);
        }
        boolean payersOwn = Objects.equals(split.getUserId(), expense.getPaidBy());
        if (!payersOwn && !Objects.equals(Boolean.TRUE.equals(split.getIsSettled()), paid)) {
            split.setIsSettled(paid);
            split.setSettledAt(paid ? LocalDateTime.now() : null);
            expenseSplitRepository.update(split);
            UUID other = isPayer ? split.getUserId() : expense.getPaidBy();
            notifyPayment(expense, split, other, userId);
        }
        return toResponse(expense, splits, userId);
    }

    /** Creator, payer or a participant; anything else reads as missing. */
    private Expense requireParty(UUID expenseId, UUID userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .filter(candidate -> candidate.getTripId() == null)
                .orElseThrow(() -> notFound("Expense not found"));
        if (userId.equals(expense.getCreatedBy()) || userId.equals(expense.getPaidBy())) return expense;
        boolean participant = expenseSplitRepository.findByExpenseId(expenseId).stream()
                .anyMatch(split -> userId.equals(split.getUserId()));
        if (!participant) throw notFound("Expense not found");
        return expense;
    }

    private Expense requireCreator(UUID expenseId, UUID userId) {
        Expense expense = requireParty(expenseId, userId);
        if (!userId.equals(expense.getCreatedBy())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the person who added this expense can change it", HttpStatus.FORBIDDEN);
        }
        return expense;
    }

    /** No trip currency to convert into: the amount is its own "trip currency" amount. */
    private static void apply(Expense expense, PersonalExpenseRequest request) {
        expense.setAmount(request.getAmount());
        expense.setCurrency(currency(request.getCurrency()));
        expense.setExchangeRate(BigDecimal.ONE);
        expense.setAmountInTripCurrency(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        expense.setExpenseDate(expenseDate(request.getExpenseDate()));
    }

    /**
     * Validates who shares the cost and builds their splits; sets the payer on the expense.
     *
     * @param previous splits before an edit, by user: a share left at the same amount keeps
     *                 whether it was settled
     */
    private List<ExpenseSplit> splits(Expense expense, PersonalExpenseRequest request, UUID creatorId,
                                      Map<UUID, ExpenseSplit> previous) {
        List<PersonalExpenseRequest.Participant> requested = request.getParticipants() == null
                ? List.of() : request.getParticipants();
        if (requested.isEmpty()) {
            requested = List.of(PersonalExpenseRequest.Participant.builder()
                    .userId(creatorId).amount(request.getAmount()).build());
        }
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (PersonalExpenseRequest.Participant participant : requested) {
            if (!userIds.add(participant.getUserId())) {
                throw invalid("Each person can be in a split once");
            }
        }
        UUID payer = request.getPaidBy() == null ? creatorId : request.getPaidBy();
        if (!payer.equals(creatorId) && !userIds.contains(payer)) {
            throw invalid("The payer must be one of the people sharing the expense");
        }
        if (!payer.equals(creatorId) && !userIds.contains(creatorId)) {
            throw invalid("You must have paid or be one of the people sharing the expense");
        }
        requireReachable(creatorId, userIds);
        // On an edit, the old payer's share was settled only because they paid; it does not carry over.
        UUID previousPayer = expense.getPaidBy();
        expense.setPaidBy(payer);

        List<BigDecimal> amounts = SPLIT_CUSTOM.equalsIgnoreCase(request.getSplitType())
                ? customAmounts(requested, expense.getAmount())
                : equalAmounts(expense.getAmount(), requested.size(), expense.getCurrency());

        List<ExpenseSplit> splits = new ArrayList<>();
        int index = 0;
        for (UUID participant : userIds) {
            BigDecimal amount = amounts.get(index++);
            ExpenseSplit before = previous.get(participant);
            boolean settled = participant.equals(payer)
                    || (before != null && Boolean.TRUE.equals(before.getIsSettled())
                        && before.getAmount() != null && before.getAmount().compareTo(amount) == 0
                        && !participant.equals(previousPayer) && payer.equals(previousPayer));
            splits.add(ExpenseSplit.builder()
                    .id(UUID.randomUUID())
                    .expenseId(expense.getId())
                    .userId(participant)
                    .amount(amount)
                    .isSettled(settled)
                    .settledAt(settled ? (before != null && before.getSettledAt() != null
                            ? before.getSettledAt() : LocalDateTime.now()) : null)
                    .build());
        }
        return splits;
    }

    /** Every other participant must be a live account that has not blocked, or been blocked by, the creator. */
    private void requireReachable(UUID creatorId, Collection<UUID> userIds) {
        List<UUID> others = userIds.stream().filter(id -> !id.equals(creatorId)).toList();
        if (others.isEmpty()) return;
        if (others.stream().anyMatch(AssistantIdentity::isAssistant)) {
            throw invalid("Pengoo cannot share an expense");
        }
        Set<UUID> found = userRepository.findByIds(others).stream().map(User::getId).collect(Collectors.toSet());
        if (!found.containsAll(others)) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND, "Some people could not be found",
                    HttpStatus.NOT_FOUND);
        }
        if (!userBlockService.blockedAmong(creatorId, others).isEmpty()) {
            throw invalid("You cannot share an expense with someone you have blocked or who blocked you");
        }
    }

    /** Even shares in the currency's minor unit; the remainder goes to the last share. */
    static List<BigDecimal> equalAmounts(BigDecimal total, int count, String currency) {
        int digits = Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
        BigDecimal each = total.divide(BigDecimal.valueOf(count), digits, RoundingMode.DOWN);
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < count - 1; i++) amounts.add(each);
        amounts.add(total.subtract(each.multiply(BigDecimal.valueOf(count - 1L))));
        return amounts;
    }

    private static List<BigDecimal> customAmounts(List<PersonalExpenseRequest.Participant> participants,
                                                  BigDecimal total) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (PersonalExpenseRequest.Participant participant : participants) {
            BigDecimal amount = participant.getAmount();
            if (amount == null || amount.signum() < 0) throw invalid("Each share needs an amount of 0 or more");
            amounts.add(amount);
            sum = sum.add(amount);
        }
        if (sum.compareTo(total) != 0) throw invalid("The shares must add up to the amount");
        return amounts;
    }

    private static String currency(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(code);
            return code;
        } catch (IllegalArgumentException e) {
            throw invalid("Unknown currency");
        }
    }

    /** Same rule as trip expenses: a day of slack for a phone clock ahead of the server's. */
    private static LocalDateTime expenseDate(LocalDateTime requested) {
        if (requested == null) return LocalDateTime.now();
        if (requested.isAfter(LocalDateTime.now().plusDays(1))) {
            throw invalid("Expense date cannot be in the future");
        }
        return requested;
    }

    /** Tells each newly included person, other than the one who added it, what their share is. */
    private void notifyAdded(Expense expense, List<ExpenseSplit> splits, Set<UUID> alreadyIncluded, UUID actorId) {
        String actorName = displayName(userRepository.findById(actorId).orElse(null));
        for (ExpenseSplit split : splits) {
            UUID recipient = split.getUserId();
            if (recipient == null || recipient.equals(actorId) || alreadyIncluded.contains(recipient)) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("personal", true);
            data.put("actorName", actorName);
            data.put("expenseName", expense.getDescription());
            data.put("amount", expense.getAmount());
            data.put("shareAmount", split.getAmount());
            data.put("currency", expense.getCurrency());
            data.put("expenseId", expense.getId().toString());
            data.put("deepLink", "/wallet");
            notificationService.createNotification(recipient, null, NotificationType.EXPENSE_ADDED,
                    null, null, data, actorId);
        }
    }

    private void notifyPayment(Expense expense, ExpenseSplit split, UUID recipient, UUID actorId) {
        if (recipient == null || recipient.equals(actorId)) return;
        Map<UUID, User> users = userRepository.findByIds(List.of(expense.getPaidBy(), split.getUserId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<String, Object> data = new HashMap<>();
        data.put("payerName", displayName(users.get(expense.getPaidBy())));
        data.put("payeeName", displayName(users.get(split.getUserId())));
        data.put("amount", split.getAmount());
        data.put("currency", expense.getCurrency());
        data.put("expenseDescription", expense.getDescription());
        data.put("expenseId", expense.getId().toString());
        data.put("isPaid", Boolean.TRUE.equals(split.getIsSettled()));
        data.put("deepLink", "/wallet");
        notificationService.createNotification(recipient, null, NotificationType.PAYMENT_MARKED,
                null, null, data, actorId);
    }

    private PersonalExpenseResponse toResponse(Expense expense, List<ExpenseSplit> splits, UUID viewerId) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(expense.getCreatedBy());
        ids.add(expense.getPaidBy());
        splits.forEach(split -> ids.add(split.getUserId()));
        ids.remove(null);
        Map<UUID, User> users = userRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        List<BigDecimal> even = splits.isEmpty() ? List.of()
                : equalAmounts(expense.getAmount(), splits.size(), expense.getCurrency());
        boolean isEven = true;
        for (int i = 0; i < splits.size(); i++) {
            if (splits.get(i).getAmount() == null || splits.get(i).getAmount().compareTo(even.get(i)) != 0) {
                isEven = false;
            }
        }
        return PersonalExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory() == null ? null : expense.getCategory().name())
                .description(expense.getDescription())
                .expenseDate(expense.getExpenseDate())
                .photoUrls(MediaAssetResponseMapper.toUrls(MediaAssetResponseMapper.toImageResponses(
                        mediaAssetRepository.findByEntity(ExpensePhotoSync.ENTITY_TYPE, expense.getId()))))
                .createdAt(expense.getCreatedAt())
                .createdBy(person(expense.getCreatedBy(), users))
                .paidBy(person(expense.getPaidBy(), users))
                .splitType(isEven ? SPLIT_EQUAL : SPLIT_CUSTOM)
                .splits(splits.stream().map(split -> PersonalExpenseResponse.Split.builder()
                        .id(split.getId())
                        .person(person(split.getUserId(), users))
                        .amount(split.getAmount())
                        .isPaid(Boolean.TRUE.equals(split.getIsSettled()))
                        .build()).toList())
                .canEdit(viewerId.equals(expense.getCreatedBy()))
                .build();
    }

    private static WalletPersonResponse person(UUID userId, Map<UUID, User> users) {
        User user = users.get(userId);
        return WalletPersonResponse.builder()
                .userId(userId)
                .displayName(displayName(user))
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .isGuest(false)
                .build();
    }

    private static String displayName(User user) {
        if (user == null) return "Someone";
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        return user.getUsername() == null || user.getUsername().isBlank() ? "Someone" : user.getUsername();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorConstant.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }
}
