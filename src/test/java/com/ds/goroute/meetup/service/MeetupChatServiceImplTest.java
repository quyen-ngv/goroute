package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The meetup group chat: its members are the host and the JOINED participants, and nobody
 * else can open it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MeetupChatService")
class MeetupChatServiceImplTest {

    private static final UUID MEETUP = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID JOINER = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();

    @Mock
    private MarketplaceChatRepository chatRepository;
    @Mock
    private MarketplaceChatService chatService;
    @Mock
    private MeetupRepository meetups;
    @Mock
    private BusinessConfigService config;
    @Mock
    private BetaAccessService betaAccess;
    @Mock
    private UserRepository users;

    private MeetupChatServiceImpl service;

    @BeforeEach
    void setUp() {
        MeetupAccess access = new MeetupAccess(config, betaAccess, meetups);
        service = new MeetupChatServiceImpl(chatRepository, chatService, meetups, access, users);
        when(config.getBoolean(BusinessConfigKey.MEETUP_ENABLED)).thenReturn(true);
        when(meetups.findById(MEETUP)).thenReturn(Optional.of(
                Meetup.builder().id(MEETUP).hostUserId(HOST).title("chạy bộ quanh hồ Tây").build()));
        when(users.findById(any())).thenReturn(Optional.of(User.builder().id(JOINER).fullName("Linh").build()));
    }

    @Nested
    @DisplayName("when a meetup is created")
    class WhenCreated {

        @Test
        @DisplayName("opens the group with the host alone, and announces nobody")
        void createsWithTheHost() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.empty());
            joined();

            service.onMeetupCreated(MEETUP);

            ArgumentCaptor<MarketplaceConversation> captor = ArgumentCaptor.forClass(MarketplaceConversation.class);
            verify(chatRepository).insertConversation(captor.capture());
            MarketplaceConversation created = captor.getValue();
            assertThat(created.getConversationType()).isEqualTo(MarketplaceConversationType.MEETUP.name());
            assertThat(created.getMeetupId()).isEqualTo(MEETUP);
            assertThat(created.getTripId()).isNull();
            assertThat(created.getStatus()).isEqualTo(MarketplaceConversationStatus.OPEN.name());
            verify(chatRepository).addMember(created.getId(), HOST, "CREATOR", created.getCreatedAt());
            verify(chatService).postSystemMessage(created.getId(), "CONVERSATION_CREATED", null, null);
            verify(chatService, never()).postSystemMessage(any(), eq("MEMBER_JOINED"), any(), any());
        }

        @Test
        @DisplayName("does nothing the second time")
        void isIdempotent() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));

            service.onMeetupCreated(MEETUP);

            verify(chatRepository, never()).insertConversation(any());
        }

        @Test
        @DisplayName("reads the winner's row when two opens race on the unique index")
        void survivesTheRace() {
            when(chatRepository.findByMeetup(MEETUP, null))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(conversation()));
            when(chatRepository.insertConversation(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

            service.onMeetupCreated(MEETUP);

            verify(chatService, never()).postSystemMessage(any(), anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("when a seat changes")
    class WhenSeatsChange {

        @Test
        @DisplayName("adds a JOINED participant and says so")
        void addsAndAnnounces() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(HOST));
            joined(JOINER);

            service.onMembershipChanged(MEETUP);

            verify(chatRepository).addMember(eq(CONVERSATION), eq(JOINER), eq("PARTICIPANT"), any());
            verify(chatService).postSystemMessage(CONVERSATION, "MEMBER_JOINED", JOINER, "Linh");
        }

        @Test
        @DisplayName("takes a leaver out and says so, but never the host")
        void removesAndAnnounces() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(HOST, JOINER));
            joined();

            service.onMembershipChanged(MEETUP);

            verify(chatRepository).markMemberLeft(eq(CONVERSATION), eq(JOINER), any());
            verify(chatRepository, never()).markMemberLeft(any(), eq(HOST), any());
            verify(chatService).postSystemMessage(CONVERSATION, "MEMBER_LEFT", JOINER, "Linh");
        }

        @Test
        @DisplayName("asks the meetup for JOINED rows only, so a request is not a member")
        void asksForJoinedRowsOnly() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(HOST));
            joined();

            service.onMembershipChanged(MEETUP);

            verify(meetups).findParticipants(eq(MEETUP), eq(List.of(MeetupParticipantStatus.JOINED)), anyInt());
            verify(chatRepository, never()).addMember(any(), any(), anyString(), any());
            verify(chatService, never()).postSystemMessage(any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("does nothing for a meetup that has no chat yet")
        void ignoresMeetupsWithoutAConversation() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.empty());

            service.onMembershipChanged(MEETUP);

            verify(chatRepository, never()).addMember(any(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("when somebody opens it")
    class WhenOpening {

        @Test
        @DisplayName("refuses somebody who has not joined")
        void refusesStrangersAndRequesters() {
            when(meetups.findParticipant(MEETUP, REQUESTER)).thenReturn(Optional.of(
                    row(REQUESTER, MeetupParticipantStatus.REQUESTED)));

            assertThatThrownBy(() -> service.open(REQUESTER, MEETUP))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getError().getCode())
                            .isEqualTo(ErrorConstant.FORBIDDEN_ERROR));
            assertThatThrownBy(() -> service.open(UUID.randomUUID(), MEETUP))
                    .isInstanceOf(BusinessException.class);
            verify(chatService, never()).get(any(), any());
        }

        @Test
        @DisplayName("refuses a missing meetup with the meetup 404")
        void missingMeetupIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(meetups.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.open(HOST, missing))
                    .satisfies(e -> assertThat(((BusinessException) e).getError().getCode())
                            .isEqualTo(ErrorConstant.MEETUP_NOT_FOUND));
        }

        @Test
        @DisplayName("lets a JOINED participant in and reconciles quietly")
        void opensForAJoinedParticipant() {
            when(meetups.findParticipant(MEETUP, JOINER)).thenReturn(Optional.of(
                    row(JOINER, MeetupParticipantStatus.JOINED)));
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));
            when(chatRepository.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(HOST));
            joined(JOINER);

            service.open(JOINER, MEETUP);

            verify(chatRepository).addMember(eq(CONVERSATION), eq(JOINER), eq("PARTICIPANT"), any());
            verify(chatService, never()).postSystemMessage(any(), eq("MEMBER_JOINED"), any(), any());
            verify(chatService).get(JOINER, CONVERSATION);
        }

        @Test
        @DisplayName("creates the chat for a meetup that predates the feature when the host opens it")
        void createsLazilyForTheHost() {
            when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.empty());
            when(chatRepository.findActiveMemberIds(any())).thenReturn(List.of(HOST));
            joined();

            service.open(HOST, MEETUP);

            verify(chatRepository).insertConversation(any());
            verify(chatService).get(eq(HOST), any());
        }
    }

    @Test
    @DisplayName("archives the group when the meetup closes early")
    void archivesOnClose() {
        when(chatRepository.findByMeetup(MEETUP, null)).thenReturn(Optional.of(conversation()));

        service.onMeetupClosed(MEETUP);

        verify(chatRepository).updateConversation(eq(CONVERSATION),
                eq(MarketplaceConversationStatus.ARCHIVED.name()), eq(null), eq(false), any());
    }

    private void joined(UUID... userIds) {
        when(meetups.findParticipants(eq(MEETUP), any(), anyInt())).thenReturn(
                java.util.Arrays.stream(userIds).map(id -> row(id, MeetupParticipantStatus.JOINED)).toList());
    }

    private MeetupParticipant row(UUID userId, MeetupParticipantStatus status) {
        return MeetupParticipant.builder().meetupId(MEETUP).userId(userId).status(status).build();
    }

    private MarketplaceConversation conversation() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.MEETUP.name())
                .meetupId(MEETUP)
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
