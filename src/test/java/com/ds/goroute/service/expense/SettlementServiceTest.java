package com.ds.goroute.service.expense;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.LedgerParticipantRequest;
import com.ds.goroute.dto.request.SettlementRequest;
import com.ds.goroute.dto.response.SettlementResponse;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Settlement;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.SettlementRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.SettlementMethod;
import com.ds.goroute.type.TripRealtimeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SettlementServiceTest {

    private final SettlementRepository settlements = mock(SettlementRepository.class);
    private final ExpenseSplitRepository splits = mock(ExpenseSplitRepository.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationHelper notifications = mock(NotificationHelper.class);
    private final TripRealtimePublisher realtime = mock(TripRealtimePublisher.class);
    private SettlementService service;

    private final UUID tripId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final Trip trip = Trip.builder().id(tripId).ownerId(owner).build();

    @BeforeEach
    void setUp() {
        service = new SettlementService(settlements, splits, trips, accessGuard,
                new ExpenseParticipants(members, users), notifications, realtime);
        when(accessGuard.requireAccess(tripId, owner)).thenReturn(trip);
        when(accessGuard.requireAccess(tripId, member)).thenReturn(trip);
        when(accessGuard.requireAccess(tripId, stranger))
                .thenThrow(new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied"));
        when(trips.findById(tripId)).thenReturn(Optional.of(trip));
        when(members.findByTripId(tripId)).thenReturn(List.of(
                TripMember.builder().id(UUID.randomUUID()).tripId(tripId).userId(member)
                        .status(MemberStatus.ACCEPTED).isGuest(false).build(),
                TripMember.builder().id(guestId).tripId(tripId).guestName("Lan").isGuest(true).build()));
        when(users.findByIds(anyCollection())).thenReturn(List.of());
        when(members.findByIds(anyCollection())).thenReturn(List.of());
        when(settlements.findById(any())).thenAnswer(invocation -> Optional.empty());
    }

    private SettlementRequest request(LedgerParticipantRequest from, LedgerParticipantRequest to, String amount) {
        return SettlementRequest.builder()
                .tripId(tripId)
                .from(from)
                .to(to)
                .amount(new BigDecimal(amount))
                .currency("vnd")
                .method(SettlementMethod.CASH)
                .note("  tiền phòng  ")
                .build();
    }

    private static LedgerParticipantRequest user(UUID id) {
        return LedgerParticipantRequest.builder().userId(id).build();
    }

    private static LedgerParticipantRequest guest(UUID id) {
        return LedgerParticipantRequest.builder().guestMemberId(id).build();
    }

    @Test
    @DisplayName("any member records a payment between two people of the trip, guests included")
    void memberRecordsAPaymentForTwoOthers() {
        SettlementResponse response = service.create(request(guest(guestId), user(owner), "150000"), member);

        ArgumentCaptor<Settlement> saved = ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).insert(saved.capture());
        Settlement settlement = saved.getValue();
        assertThat(settlement.getTripId()).isEqualTo(tripId);
        assertThat(settlement.getFromGuestMemberId()).isEqualTo(guestId);
        assertThat(settlement.getFromUserId()).isNull();
        assertThat(settlement.getToUserId()).isEqualTo(owner);
        assertThat(settlement.getCurrency()).isEqualTo("VND");
        assertThat(settlement.getNote()).isEqualTo("tiền phòng");
        assertThat(settlement.getMethod()).isEqualTo(SettlementMethod.CASH);
        assertThat(settlement.getCreatedBy()).isEqualTo(member);
        assertThat(response.getFrom().getDisplayName()).isEqualTo("Guest");
        verify(realtime).publishAfterCommit(eq(TripRealtimeEventType.LEDGER_UPDATED), eq(tripId), any(), eq(member));
    }

    @Test
    @DisplayName("the side that did not record the payment is told, the recorder is not")
    void notifiesTheOtherSideOnly() {
        service.create(request(user(member), user(owner), "50000"), member);

        ArgumentCaptor<Collection<UUID>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(notifications).emitGeneric(eq(tripId), eq(member), eq(NotificationType.SETTLEMENT_ADDED),
                any(), recipients.capture(), isNull());
        assertThat(recipients.getValue()).containsExactly(owner);
    }

    @Test
    @DisplayName("someone outside the trip cannot record a payment")
    void strangerIsRefused() {
        assertThatThrownBy(() -> service.create(request(user(member), user(owner), "50000"), stranger))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
        verify(settlements, never()).insert(any());
    }

    @Test
    @DisplayName("both sides must be people of the trip")
    void outsiderAsSideIsRefused() {
        assertThatThrownBy(() -> service.create(request(user(stranger), user(owner), "50000"), member))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("members of the trip");
        assertThatThrownBy(() -> service.create(request(guest(UUID.randomUUID()), user(owner), "50000"), member))
                .isInstanceOf(BusinessException.class);
        verify(settlements, never()).insert(any());
    }

    @Test
    @DisplayName("nobody pays themself, and a payment needs a trip")
    void refusesSelfPaymentAndMissingTrip() {
        assertThatThrownBy(() -> service.create(request(user(owner), user(owner), "50000"), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("themself");
        SettlementRequest noTrip = request(user(member), user(owner), "50000");
        noTrip.setTripId(null);
        assertThatThrownBy(() -> service.create(noTrip, member)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a payment in the future is refused")
    void futurePaymentIsRefused() {
        SettlementRequest future = request(user(member), user(owner), "50000");
        future.setPaidAt(LocalDateTime.now().plusDays(3));

        assertThatThrownBy(() -> service.create(future, member)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deleting the settlement behind an old build's 'paid' mark makes that share unpaid again")
    void deleteUnmarksTheLinkedShare() {
        UUID settlementId = UUID.randomUUID();
        UUID splitId = UUID.randomUUID();
        when(settlements.findById(settlementId)).thenReturn(Optional.of(Settlement.builder()
                .id(settlementId).tripId(tripId).fromUserId(member).toUserId(owner)
                .amount(new BigDecimal("50000")).currency("VND").splitId(splitId).createdBy(member).build()));
        ExpenseSplit split = ExpenseSplit.builder().id(splitId).userId(member).isSettled(true)
                .settledAt(LocalDateTime.now()).build();
        when(splits.findById(splitId)).thenReturn(split);

        service.delete(settlementId, owner);

        verify(settlements).softDelete(settlementId);
        assertThat(split.getIsSettled()).isFalse();
        assertThat(split.getSettledAt()).isNull();
        verify(splits).update(split);
    }

    @Test
    @DisplayName("only the recorder, either side or the trip owner may change a payment")
    void otherMembersCannotDelete() {
        UUID settlementId = UUID.randomUUID();
        UUID otherMember = UUID.randomUUID();
        when(settlements.findById(settlementId)).thenReturn(Optional.of(Settlement.builder()
                .id(settlementId).tripId(tripId).fromGuestMemberId(guestId).toUserId(owner)
                .amount(new BigDecimal("50000")).currency("VND").createdBy(owner).build()));
        when(accessGuard.canRead(tripId, otherMember)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(settlementId, otherMember))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only whoever recorded");
        verify(settlements, never()).softDelete(any());
    }

    @Test
    @DisplayName("a payment can be restored after an undo")
    void restoreBringsItBack() {
        UUID settlementId = UUID.randomUUID();
        when(settlements.findById(settlementId)).thenReturn(Optional.of(Settlement.builder()
                .id(settlementId).tripId(tripId).fromUserId(member).toUserId(owner)
                .amount(new BigDecimal("50000")).currency("VND").createdBy(member)
                .deletedAt(LocalDateTime.now()).build()));

        service.restore(settlementId, member);

        verify(settlements).restore(settlementId);
        verify(realtime).publishAfterCommit(eq(TripRealtimeEventType.LEDGER_UPDATED), eq(tripId), eq(settlementId), eq(member));
    }
}
