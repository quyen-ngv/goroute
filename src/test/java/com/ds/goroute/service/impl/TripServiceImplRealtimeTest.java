package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.repository.TripHelpfulVoteRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripAccessRevokedReason;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.TripStatus;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The per-user and trip-topic events a membership or trip change must produce. */
class TripServiceImplRealtimeTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final ExpenseSplitRepository expenseSplitRepository = mock(ExpenseSplitRepository.class);
    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final TripRealtimePublisher tripRealtimePublisher = mock(TripRealtimePublisher.class);
    private final NotificationHelper notificationHelper = mock(NotificationHelper.class);

    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                tripRepository,
                mock(TripAccessGuard.class),
                mock(com.ds.goroute.service.TripChatService.class),
                tripMemberRepository,
                mock(UserRepository.class),
                mock(ActivityRepository.class),
                expenseRepository,
                expenseSplitRepository,
                mock(CheckinRepository.class),
                mock(TripNoteRepository.class),
                notificationHelper,
                mock(LocationImageService.class),
                mock(ExpenseService.class),
                exchangeRateService,
                mock(UserReviewRepository.class),
                mock(UserReviewProfileRepository.class),
                mock(ReviewHelpfulVoteRepository.class),
                mock(TripHelpfulVoteRepository.class),
                mock(PlaceScoreRepository.class),
                mock(MediaAssetRepository.class),
                mock(ImageStorageCleanupService.class),
                tripRealtimePublisher,
                mock(TripDestinationRepository.class),
                mock(SocialNotificationService.class),
                mock(UserCheckinService.class),
                Runnable::run);
        // Notification payloads are immutable maps, which refuse the null a bare mock returns.
        when(notificationHelper.actorName(any())).thenReturn("Someone");
        when(notificationHelper.tripName(any())).thenReturn("Trip");
    }

    @Test
    void deletingATripTellsTheOwnerAndEveryMemberWithAnAccount() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID accepted = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID alreadyLeft = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, ownerId)));
        when(tripMemberRepository.findByTripId(tripId)).thenReturn(List.of(
                member(tripId, accepted, MemberStatus.ACCEPTED),
                member(tripId, pending, MemberStatus.PENDING),
                member(tripId, alreadyLeft, MemberStatus.LEFT),
                guest(tripId)));

        service.deleteTrip(tripId, ownerId);

        verify(tripRealtimePublisher).publishAfterCommit(
                TripRealtimeEventType.TRIP_DELETED, tripId, tripId, ownerId);
        assertThat(revokedUsers(tripId, TripAccessRevokedReason.DELETED))
                .containsExactlyInAnyOrder(ownerId, accepted, pending);
    }

    @Test
    void removingAnAcceptedMemberTellsThatMember() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        TripMember removed = member(tripId, removedUserId, MemberStatus.ACCEPTED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, ownerId)));
        when(tripMemberRepository.findById(removed.getId())).thenReturn(Optional.of(removed));

        service.removeMember(tripId, removed.getId(), ownerId);

        verify(tripRealtimePublisher).publishAfterCommit(
                TripRealtimeEventType.MEMBER_REMOVED, tripId, removed.getId(), ownerId);
        assertThat(revokedUsers(tripId, TripAccessRevokedReason.REMOVED)).containsExactly(removedUserId);
    }

    @Test
    void removingAGuestTellsNobodyBecauseAGuestHasNoAccount() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TripMember guest = guest(tripId);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, ownerId)));
        when(tripMemberRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        service.removeMember(tripId, guest.getId(), ownerId);

        assertThat(revokedUsers(tripId, TripAccessRevokedReason.REMOVED)).isEmpty();
    }

    @Test
    void leavingTellsTheLeaverOnTheirOwnTopic() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, UUID.randomUUID())));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, userId))
                .thenReturn(Optional.of(member(tripId, userId, MemberStatus.ACCEPTED)));

        service.leaveTrip(tripId, userId);

        assertThat(revokedUsers(tripId, TripAccessRevokedReason.LEFT)).containsExactly(userId);
    }

    @Test
    void decliningAnInvitationTellsTheDecliner() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, UUID.randomUUID())));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, userId))
                .thenReturn(Optional.of(member(tripId, userId, MemberStatus.PENDING)));

        service.declineInvite(tripId, userId);

        assertThat(revokedUsers(tripId, TripAccessRevokedReason.DECLINED)).containsExactly(userId);
    }

    @Test
    void linkingAGuestAlsoInvalidatesExpensesBecauseSplitsMoved() {
        UUID tripId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        TripMember guest = guest(tripId);
        when(tripMemberRepository.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, targetUserId))
                .thenReturn(Optional.of(member(tripId, targetUserId, MemberStatus.ACCEPTED)));
        when(expenseSplitRepository.findByGuestMemberId(guest.getId())).thenReturn(List.of());

        service.linkGuestToUser(tripId, guest.getId(),
                LinkGuestRequest.builder().targetUserId(targetUserId).build(), editorId);

        verify(tripRealtimePublisher).publishAfterCommit(
                eq(TripRealtimeEventType.EXPENSE_UPDATED), eq(tripId), isNull(), eq(editorId));
    }

    @Test
    void aCurrencyChangeNamesCurrencyAndTheConvertedBudget() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Trip trip = trip(tripId, ownerId);
        trip.setCurrency("VND");
        trip.setBudget(new BigDecimal("1000000"));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(exchangeRateService.convert(any(), eq("VND"), eq("USD"))).thenReturn(new BigDecimal("40"));

        service.updateTrip(tripId, UpdateTripRequest.builder().currency("USD").build(), ownerId);

        assertThat(updatedFields(tripId, ownerId)).containsExactlyInAnyOrder("currency", "budget");
    }

    @Test
    void anUnchangedBudgetWrittenWithADifferentScaleIsNotReportedAsChanged() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Trip trip = trip(tripId, ownerId);
        trip.setBudget(new BigDecimal("100"));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        service.updateTrip(tripId, UpdateTripRequest.builder()
                .budget(new BigDecimal("100.00"))
                .name("Renamed")
                .build(), ownerId);

        assertThat(updatedFields(tripId, ownerId)).containsExactly("name");
    }

    @SuppressWarnings("unchecked")
    private List<String> updatedFields(UUID tripId, UUID actorId) {
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(tripRealtimePublisher).publishAfterCommit(
                eq(TripRealtimeEventType.TRIP_UPDATED), eq(tripId), eq(tripId), eq(actorId), payload.capture());
        return (List<String>) payload.getValue().get("fields");
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> revokedUsers(UUID tripId, TripAccessRevokedReason reason) {
        ArgumentCaptor<Collection<UUID>> users = ArgumentCaptor.forClass(Collection.class);
        verify(tripRealtimePublisher).publishAccessRevokedAfterCommit(eq(tripId), users.capture(), eq(reason));
        return users.getValue();
    }

    private Trip trip(UUID tripId, UUID ownerId) {
        return Trip.builder()
                .id(tripId)
                .ownerId(ownerId)
                .name("Trip")
                .destination("Hanoi")
                .status(TripStatus.PLANNING)
                .visibility(TripVisibility.PRIVATE)
                .build();
    }

    private TripMember member(UUID tripId, UUID userId, MemberStatus status) {
        return TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .userId(userId)
                .role(MemberRole.EDITOR)
                .status(status)
                .invitedBy(UUID.randomUUID())
                .isGuest(false)
                .build();
    }

    private TripMember guest(UUID tripId) {
        return TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .role(MemberRole.VIEWER)
                .status(MemberStatus.ACCEPTED)
                .isGuest(true)
                .guestName("Guest")
                .build();
    }
}
