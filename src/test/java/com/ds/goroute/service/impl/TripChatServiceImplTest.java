package com.ds.goroute.service.impl;

import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The trip group chat, which exists to have exactly one property: its members are the
 * trip's members, and nobody else ever sees it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TripChatService")
class TripChatServiceImplTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();

    @Mock
    private MarketplaceChatRepository chatRepository;
    @Mock
    private MarketplaceChatService chatService;
    @Mock
    private TripRepository trips;
    @Mock
    private TripMemberRepository tripMembers;
    @Mock
    private UserRepository users;

    private TripChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripChatServiceImpl(chatRepository, chatService, trips, tripMembers, users);
        when(trips.findById(TRIP)).thenReturn(Optional.of(Trip.builder().id(TRIP).name("Đà Lạt").build()));
        when(users.findById(any())).thenReturn(Optional.of(User.builder().id(FRIEND).fullName("Linh").build()));
    }

    @Nested
    @DisplayName("when a trip is created")
    class WhenTripIsCreated {

        @Test
        @DisplayName("opens the group with everybody already in the trip, and announces nobody")
        void createsWithCurrentMembers() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.empty());
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED),
                    member(FRIEND, MemberRole.EDITOR, MemberStatus.ACCEPTED)));

            service.onTripCreated(TRIP);

            ArgumentCaptor<MarketplaceConversation> captor = ArgumentCaptor.forClass(MarketplaceConversation.class);
            verify(chatRepository).insertConversation(captor.capture());
            MarketplaceConversation created = captor.getValue();
            assertThat(created.getConversationType()).isEqualTo(MarketplaceConversationType.TRIP.name());
            assertThat(created.getTripId()).isEqualTo(TRIP);
            assertThat(created.getStatus()).isEqualTo(MarketplaceConversationStatus.OPEN.name());

            verify(chatRepository).addMember(created.getId(), OWNER, "CREATOR", created.getCreatedAt());
            verify(chatRepository).addMember(created.getId(), FRIEND, "PARTICIPANT", created.getCreatedAt());
            verify(chatService).postSystemMessage(created.getId(), "CONVERSATION_CREATED", null, null);
            verify(chatService, never()).postSystemMessage(any(), eq("MEMBER_JOINED"), any(), any());
        }

        @Test
        @DisplayName("does nothing the second time, so a retried create makes one group")
        void isIdempotent() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));

            service.onTripCreated(TRIP);

            verify(chatRepository, never()).insertConversation(any());
        }

        @Test
        @DisplayName("reads the winner's row when two opens race on the unique index")
        void survivesTheRace() {
            when(chatRepository.findByTrip(TRIP, null))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(conversation()));
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED)));
            when(chatRepository.insertConversation(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

            service.onTripCreated(TRIP);

            verify(chatService, never()).postSystemMessage(any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("leaves a guest with no account out: there is nobody to talk to")
        void skipsGuests() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.empty());
            TripMember guest = member(null, MemberRole.VIEWER, MemberStatus.ACCEPTED);
            guest.setIsGuest(true);
            guest.setGuestName("Bạn của Linh");
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED), guest));

            service.onTripCreated(TRIP);

            verify(chatRepository).addMember(any(), eq(OWNER), anyString(), any());
            verify(chatRepository, never()).addMember(any(), eq(null), anyString(), any());
        }

        @Test
        @DisplayName("leaves an invitation nobody accepted out")
        void skipsPendingInvitations() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.empty());
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED),
                    member(FRIEND, MemberRole.EDITOR, MemberStatus.PENDING)));

            service.onTripCreated(TRIP);

            verify(chatRepository, never()).addMember(any(), eq(FRIEND), anyString(), any());
        }
    }

    @Nested
    @DisplayName("when the trip's membership changes")
    class WhenMembershipChanges {

        @Test
        @DisplayName("adds the newcomer and says so in the thread")
        void addsAndAnnounces() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(OWNER));
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED),
                    member(FRIEND, MemberRole.EDITOR, MemberStatus.ACCEPTED)));

            service.onMembershipChanged(TRIP);

            verify(chatRepository).addMember(eq(CONVERSATION), eq(FRIEND), eq("PARTICIPANT"), any());
            verify(chatService).postSystemMessage(CONVERSATION, "MEMBER_JOINED", FRIEND, "Linh");
        }

        @Test
        @DisplayName("takes the leaver out and says so")
        void removesAndAnnounces() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(OWNER, FRIEND));
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED)));

            service.onMembershipChanged(TRIP);

            verify(chatRepository).markMemberLeft(eq(CONVERSATION), eq(FRIEND), any());
            verify(chatService).postSystemMessage(CONVERSATION, "MEMBER_LEFT", FRIEND, "Linh");
        }

        @Test
        @DisplayName("says nothing when nothing moved")
        void staysQuietWhenUnchanged() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(OWNER));
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED)));

            service.onMembershipChanged(TRIP);

            verify(chatService, never()).postSystemMessage(any(), anyString(), any(), any());
            verify(chatRepository, never()).markMemberLeft(any(), any(), any());
        }

        @Test
        @DisplayName("does nothing for a trip that never opened its chat")
        void ignoresTripsWithoutAConversation() {
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.empty());

            service.onMembershipChanged(TRIP);

            verify(chatRepository, never()).addMember(any(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("when somebody opens it")
    class WhenOpening {

        @Test
        @DisplayName("refuses somebody who is not in the trip")
        void refusesStrangers() {
            when(tripMembers.findByTripIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.open(STRANGER, TRIP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        @DisplayName("refuses somebody whose invitation is still pending")
        void refusesPendingInvitees() {
            when(tripMembers.findByTripIdAndUserId(TRIP, FRIEND))
                    .thenReturn(Optional.of(member(FRIEND, MemberRole.EDITOR, MemberStatus.PENDING)));

            assertThatThrownBy(() -> service.open(FRIEND, TRIP)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("reconciles quietly, so a change made while away is not announced late")
        void reconcilesWithoutAnnouncing() {
            when(tripMembers.findByTripIdAndUserId(TRIP, OWNER))
                    .thenReturn(Optional.of(member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED)));
            when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(OWNER));
            when(tripMembers.findByTripId(TRIP)).thenReturn(List.of(
                    member(OWNER, MemberRole.OWNER, MemberStatus.ACCEPTED),
                    member(FRIEND, MemberRole.EDITOR, MemberStatus.ACCEPTED)));

            service.open(OWNER, TRIP);

            verify(chatRepository).addMember(eq(CONVERSATION), eq(FRIEND), eq("PARTICIPANT"), any());
            verify(chatService, never()).postSystemMessage(any(), eq("MEMBER_JOINED"), any(), any());
            verify(chatService).get(OWNER, CONVERSATION);
        }
    }

    @Test
    @DisplayName("retires the group with its trip rather than deleting the transcript")
    void archivesOnTripDeletion() {
        when(chatRepository.findByTrip(TRIP, null)).thenReturn(Optional.of(conversation()));

        service.onTripDeleted(TRIP);

        verify(chatRepository).updateConversation(eq(CONVERSATION),
                eq(MarketplaceConversationStatus.ARCHIVED.name()), eq(null), eq(false), any());
    }

    private MarketplaceConversation conversation() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.TRIP.name())
                .tripId(TRIP)
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private TripMember member(UUID userId, MemberRole role, MemberStatus status) {
        return TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(TRIP)
                .userId(userId)
                .role(role)
                .status(status)
                .build();
    }
}
