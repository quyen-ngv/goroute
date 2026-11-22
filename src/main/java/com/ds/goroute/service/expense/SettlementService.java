package com.ds.goroute.service.expense;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.LedgerParticipantRequest;
import com.ds.goroute.dto.request.SettlementRequest;
import com.ds.goroute.dto.response.SettlementPageResponse;
import com.ds.goroute.dto.response.SettlementResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Settlement;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.SettlementRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.expense.ExpenseParticipants.TripRoster;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.SettlementMethod;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Settle up (epic 05 §2.4f): a payment from one person to another inside a trip. A partial
 * payment is a smaller settlement; a delete is soft and can be restored. Balances are computed from these rows ({@link LedgerService}); nothing is stored.
 *
 * <p>Who may record: the trip owner or any ACCEPTED member (anyone in the group, as in Splitwise),
 * for any two people of the trip. Who may change or delete one: whoever recorded it, either side
 * (when a user), or the trip owner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    private final SettlementRepository settlementRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final TripRepository tripRepository;
    private final TripAccessGuard tripAccessGuard;
    private final ExpenseParticipants participants;
    private final NotificationHelper notificationHelper;
    private final TripRealtimePublisher tripRealtimePublisher;

    @Transactional
    public SettlementResponse create(SettlementRequest request, UUID userId) {
        if (request.getTripId() == null) throw invalid("A payment belongs to a trip");
        Parties parties = parties(request.getTripId(), request, userId, null);
        Settlement settlement = Settlement.builder()
                .id(UUID.randomUUID())
                .tripId(request.getTripId())
                .createdBy(userId)
                .build();
        applyFields(settlement, parties, request);
        settlementRepository.insert(settlement);
        Settlement saved = settlementRepository.findById(settlement.getId()).orElse(settlement);
        Map<ParticipantRef, WalletPersonResponse> people = describe(saved);

        notifyAdded(saved, userId, people);
        publish(saved, userId);
        log.info("Settlement {} recorded in {} by {}", saved.getId(), saved.getTripId(), userId);
        return toResponse(saved, people);
    }

    @Transactional
    public SettlementResponse update(UUID settlementId, SettlementRequest request, UUID userId) {
        Settlement settlement = requireManageable(settlementId, userId);
        if (settlement.getDeletedAt() != null) throw notFound();
        if (request.getTripId() != null && !request.getTripId().equals(settlement.getTripId())) {
            throw invalid("A settlement cannot move to another trip");
        }
        Set<ParticipantRef> current = new LinkedHashSet<>();
        current.add(from(settlement));
        current.add(to(settlement));
        Parties parties = parties(settlement.getTripId(), request, userId, current);
        applyFields(settlement, parties, request);
        settlementRepository.update(settlement);
        Settlement saved = settlementRepository.findById(settlementId).orElse(settlement);
        Map<ParticipantRef, WalletPersonResponse> people = describe(saved);
        publish(saved, userId);
        return toResponse(saved, people);
    }

    /** Soft delete; a share an old build marked paid through this settlement reads unpaid again. */
    @Transactional
    public void delete(UUID settlementId, UUID userId) {
        Settlement settlement = requireManageable(settlementId, userId);
        if (settlement.getDeletedAt() != null) return;
        settlementRepository.softDelete(settlementId);
        if (settlement.getSplitId() != null) {
            ExpenseSplit split = expenseSplitRepository.findById(settlement.getSplitId());
            if (split != null) {
                split.setIsSettled(false);
                split.setSettledAt(null);
                expenseSplitRepository.update(split);
            }
        }
        publish(settlement, userId);
    }

    @Transactional
    public SettlementResponse restore(UUID settlementId, UUID userId) {
        Settlement settlement = requireManageable(settlementId, userId);
        if (settlement.getDeletedAt() != null) {
            settlementRepository.restore(settlementId);
            if (settlement.getSplitId() != null) {
                ExpenseSplit split = expenseSplitRepository.findById(settlement.getSplitId());
                if (split != null && settlementRepository.findActiveBySplitIds(List.of(split.getId())).size() == 1) {
                    split.setIsSettled(true);
                    split.setSettledAt(LocalDateTime.now());
                    expenseSplitRepository.update(split);
                }
            }
            publish(settlement, userId);
        }
        Settlement saved = settlementRepository.findById(settlementId).orElse(settlement);
        return toResponse(saved, describe(saved));
    }

    /** Newest payment first. */
    @Transactional(readOnly = true)
    public SettlementPageResponse tripPage(UUID tripId, int page, int size, boolean includeDeleted, UUID userId) {
        tripAccessGuard.requireAccess(tripId, userId);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        int pageNumber = Math.max(0, page);
        List<Settlement> rows = settlementRepository.findTripPage(tripId, includeDeleted, pageSize, pageNumber * pageSize);
        Set<ParticipantRef> refs = new LinkedHashSet<>();
        rows.forEach(row -> refs.addAll(refsOf(row)));
        Map<ParticipantRef, WalletPersonResponse> people = participants.describe(refs);
        return SettlementPageResponse.builder()
                .items(rows.stream().map(row -> toResponse(row, people)).toList())
                .page(pageNumber)
                .size(pageSize)
                .total(settlementRepository.countTrip(tripId, includeDeleted))
                .build();
    }

    private record Parties(ParticipantRef from, ParticipantRef to) {
    }

    /**
     * Checks who pays whom.
     *
     * @param alreadyOn on an edit, the people already on the settlement stay allowed even if they
     *                  have since left the trip
     */
    private Parties parties(UUID tripId, SettlementRequest request, UUID userId, Set<ParticipantRef> alreadyOn) {
        LedgerParticipantRequest fromRequest = request.getFrom();
        LedgerParticipantRequest toRequest = request.getTo();
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        TripRoster roster = participants.roster(trip);
        ParticipantRef from = fromRequest == null ? null
                : roster.resolve(fromRequest.getUserId(), fromRequest.getGuestMemberId());
        ParticipantRef to = toRequest == null ? null
                : roster.resolve(toRequest.getUserId(), toRequest.getGuestMemberId());
        if (from == null || to == null) throw invalid("A settlement is between two people");
        if (from.equals(to)) throw invalid("Someone cannot pay themself");
        for (ParticipantRef side : List.of(from, to)) {
            boolean allowed = roster.contains(side) || (alreadyOn != null && alreadyOn.contains(side));
            if (!allowed) throw invalid("Both people must be members of the trip");
        }
        return new Parties(from, to);
    }

    private static void applyFields(Settlement settlement, Parties parties, SettlementRequest request) {
        if ((request.getOriginalAmount() == null) != (request.getOriginalCurrency() == null)) {
            throw invalid("originalAmount and originalCurrency go together");
        }
        LocalDateTime paidAt = request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt();
        if (paidAt.isAfter(LocalDateTime.now().plusDays(1))) throw invalid("A payment cannot be in the future");
        settlement.setFromUserId(parties.from().userId());
        settlement.setFromGuestMemberId(parties.from().guestMemberId());
        settlement.setToUserId(parties.to().userId());
        settlement.setToGuestMemberId(parties.to().guestMemberId());
        settlement.setAmount(request.getAmount());
        settlement.setCurrency(code(request.getCurrency()));
        settlement.setMethod(request.getMethod() == null ? SettlementMethod.OTHER : request.getMethod());
        settlement.setNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
        settlement.setPaidAt(paidAt);
        settlement.setOriginalAmount(request.getOriginalAmount());
        settlement.setOriginalCurrency(request.getOriginalCurrency() == null ? null : code(request.getOriginalCurrency()));
    }

    /** The settlement, if this user recorded it, is one of its users, or owns its trip. */
    private Settlement requireManageable(UUID settlementId, UUID userId) {
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(SettlementService::notFound);
        Trip trip = tripRepository.findById(settlement.getTripId()).orElseThrow(SettlementService::notFound);
        boolean isSide = userId.equals(settlement.getFromUserId()) || userId.equals(settlement.getToUserId());
        boolean isCreator = userId.equals(settlement.getCreatedBy());
        boolean isOwner = userId.equals(trip.getOwnerId());
        if (isSide || isCreator || isOwner) return settlement;
        if (tripAccessGuard.canRead(trip.getId(), userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only whoever recorded this payment, the two people in it or the trip owner can change it",
                    HttpStatus.FORBIDDEN);
        }
        throw notFound();
    }

    private void notifyAdded(Settlement settlement, UUID actorId, Map<ParticipantRef, WalletPersonResponse> people) {
        List<UUID> recipients = new ArrayList<>();
        for (UUID side : new UUID[]{settlement.getFromUserId(), settlement.getToUserId()}) {
            if (side != null && !side.equals(actorId)) recipients.add(side);
        }
        if (recipients.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("actorName", notificationHelper.actorName(actorId));
        data.put("payerName", ExpenseParticipants.person(from(settlement), people).getDisplayName());
        data.put("payeeName", ExpenseParticipants.person(to(settlement), people).getDisplayName());
        data.put("amount", settlement.getAmount());
        data.put("currency", settlement.getCurrency());
        data.put("settlementId", settlement.getId().toString());
        data.put("deepLink", "/trip/" + settlement.getTripId() + "/expenses");
        notificationHelper.emitGeneric(settlement.getTripId(), actorId, NotificationType.SETTLEMENT_ADDED,
                data, recipients, null);
    }

    private void publish(Settlement settlement, UUID actorId) {
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED,
                settlement.getTripId(), settlement.getId(), actorId);
    }

    private Map<ParticipantRef, WalletPersonResponse> describe(Settlement settlement) {
        return participants.describe(refsOf(settlement));
    }

    private static List<ParticipantRef> refsOf(Settlement settlement) {
        List<ParticipantRef> refs = new ArrayList<>();
        refs.add(from(settlement));
        refs.add(to(settlement));
        if (settlement.getCreatedBy() != null) refs.add(ParticipantRef.user(settlement.getCreatedBy()));
        refs.removeIf(Objects::isNull);
        return refs;
    }

    static ParticipantRef from(Settlement settlement) {
        return ParticipantRef.of(settlement.getFromUserId(), settlement.getFromGuestMemberId());
    }

    static ParticipantRef to(Settlement settlement) {
        return ParticipantRef.of(settlement.getToUserId(), settlement.getToGuestMemberId());
    }

    static SettlementResponse toResponse(Settlement settlement, Map<ParticipantRef, WalletPersonResponse> people) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .tripId(settlement.getTripId())
                .from(ExpenseParticipants.person(from(settlement), people))
                .to(ExpenseParticipants.person(to(settlement), people))
                .amount(settlement.getAmount())
                .currency(settlement.getCurrency())
                .method(settlement.getMethod() == null ? SettlementMethod.OTHER.name() : settlement.getMethod().name())
                .note(settlement.getNote())
                .paidAt(settlement.getPaidAt())
                .originalAmount(settlement.getOriginalAmount())
                .originalCurrency(settlement.getOriginalCurrency())
                .createdBy(settlement.getCreatedBy() == null ? null
                        : ExpenseParticipants.person(ParticipantRef.user(settlement.getCreatedBy()), people))
                .createdAt(settlement.getCreatedAt())
                .deleted(settlement.getDeletedAt() != null)
                .build();
    }

    private static String code(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE.matcher(code).matches()) throw invalid("Currency must be a 3-letter code");
        return code;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Settlement not found", HttpStatus.NOT_FOUND);
    }
}
