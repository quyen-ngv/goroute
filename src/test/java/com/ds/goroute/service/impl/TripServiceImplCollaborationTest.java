package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
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
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripStatus;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripServiceImplCollaborationTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final CheckinRepository checkinRepository = mock(CheckinRepository.class);
    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final TripDestinationRepository tripDestinationRepository = mock(TripDestinationRepository.class);
    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final NotificationHelper notificationHelper = mock(NotificationHelper.class);

    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                tripRepository,
                mock(TripAccessGuard.class),
                tripMemberRepository,
                userRepository,
                activityRepository,
                expenseRepository,
                mock(ExpenseSplitRepository.class),
                checkinRepository,
                mock(TripNoteRepository.class),
                notificationHelper,
                mock(LocationImageService.class),
                mock(ExpenseService.class),
                mock(ExchangeRateService.class),
                mock(UserReviewRepository.class),
                mock(UserReviewProfileRepository.class),
                mock(ReviewHelpfulVoteRepository.class),
                mock(TripHelpfulVoteRepository.class),
                mock(PlaceScoreRepository.class),
                mediaAssetRepository,
                mock(ImageStorageCleanupService.class),
                mock(TripRealtimePublisher.class),
                tripDestinationRepository,
                mock(SocialNotificationService.class),
                Runnable::run);
    }

    @Test
    void pendingMemberCannotReadTripDetailBeforeAcceptingInvitation() {
        UUID tripId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Trip trip = trip(tripId, UUID.randomUUID());
        TripMember pending = TripMember.builder()
                .tripId(tripId)
                .userId(inviteeId)
                .status(MemberStatus.PENDING)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, inviteeId))
                .thenReturn(Optional.of(pending));

        assertThrows(RuntimeException.class, () -> service.getTripDetail(tripId, inviteeId));
    }

    @Test
    void ownerCanReinviteUserWhosePreviousInvitationWasDeclined() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Trip trip = trip(tripId, ownerId);
        User invitee = User.builder()
                .id(inviteeId)
                .email("invitee@example.com")
                .username("invitee")
                .fullName("Invitee")
                .build();
        TripMember declined = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .userId(inviteeId)
                .role(MemberRole.VIEWER)
                .status(MemberStatus.DECLINED)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findByEmail(invitee.getEmail())).thenReturn(Optional.of(invitee));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, inviteeId))
                .thenReturn(Optional.of(declined));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));

        service.inviteMember(
                tripId,
                InviteMemberRequest.builder()
                        .identifier(invitee.getEmail())
                        .role("EDITOR")
                        .build(),
                ownerId);

        assertEquals(MemberStatus.PENDING, declined.getStatus());
        assertEquals(MemberRole.EDITOR, declined.getRole());
        assertEquals(ownerId, declined.getInvitedBy());
        verify(tripMemberRepository).updateById(declined);
        verify(tripMemberRepository, never()).insert(any(TripMember.class));
    }

    @Test
    void userCanSubmitJoinRequestAgainAfterDeclining() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Trip trip = trip(tripId, ownerId);
        TripMember declined = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .userId(userId)
                .role(MemberRole.VIEWER)
                .status(MemberStatus.DECLINED)
                .build();

        when(tripRepository.findByShareCode("ABC123")).thenReturn(Optional.of(trip));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, userId))
                .thenReturn(Optional.of(declined));
        when(tripMemberRepository.findByTripId(tripId)).thenReturn(List.of(declined));
        when(activityRepository.findByTripId(tripId)).thenReturn(List.of());
        when(checkinRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());
        when(mediaAssetRepository.findTripMemoriesByTripId(tripId)).thenReturn(List.of());
        when(tripDestinationRepository.findByTripId(tripId)).thenReturn(List.of(destination()));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(User.builder()
                .id(ownerId)
                .fullName("Owner")
                .build()));
        when(notificationHelper.actorName(userId)).thenReturn("User");

        service.joinTripByCode("ABC123", userId);

        assertEquals(MemberStatus.PENDING, declined.getStatus());
        assertEquals(MemberRole.VIEWER, declined.getRole());
        assertEquals(null, declined.getInvitedBy());
        verify(tripMemberRepository).updateById(declined);
        verify(tripMemberRepository, never()).insert(any(TripMember.class));
    }

    @Test
    void invitationAcceptCannotAcceptAJoinByCodeRequestDirectly() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripMember joinRequest = TripMember.builder()
                .tripId(tripId)
                .userId(userId)
                .status(MemberStatus.PENDING)
                .invitedBy(null)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, UUID.randomUUID())));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, userId))
                .thenReturn(Optional.of(joinRequest));

        assertThrows(RuntimeException.class, () -> service.acceptInvite(tripId, userId));
        verify(tripMemberRepository, never()).updateById(joinRequest);
    }

    private Trip trip(UUID tripId, UUID ownerId) {
        return Trip.builder()
                .id(tripId)
                .ownerId(ownerId)
                .name("Trip")
                .destination("Hanoi")
                .coverImageUrl("https://example.com/cover.jpg")
                .status(TripStatus.PLANNING)
                .visibility(TripVisibility.PRIVATE)
                .build();
    }

    private TripDestination destination() {
        return TripDestination.builder()
                .id(UUID.randomUUID())
                .name("Hanoi")
                .orderIndex(0)
                .build();
    }
}
