package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.EditMarketplaceMessageRequest;
import com.ds.goroute.dto.request.MuteConversationRequest;
import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.StartMarketplaceConversationRequest;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.MarketplaceMessage;
import com.ds.goroute.entity.MarketplaceMessageReaction;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MarketplaceChatMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.WebSocketService;
import com.ds.goroute.service.notification.ConversationNotifier;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MarketplaceMessageType;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The chat service as a chat: replies, edits, deletes, reactions, mute -- and the two rules
 * that are not features, namely who may read a trip thread and who may never.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MarketplaceChatService, as a chat")
class MarketplaceChatServiceChatFeaturesTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID OTHER_CONVERSATION = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID MESSAGE = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();

    @Mock private MarketplaceChatRepository repo;
    @Mock private MarketplaceChatMapper chatMapper;
    @Mock private HotelMarketplaceRepository hotelRepo;
    @Mock private ActivityCommerceRepository activityRepo;
    @Mock private HostOrganizationRepository orgRepo;
    @Mock private UserRepository userRepo;
    @Mock private PartnerAuthorizationService authorization;
    @Mock private MarketplaceHistoryService history;
    @Mock private WebSocketService webSocketService;
    @Mock private BusinessConfigService businessConfig;
    @Mock private ConversationNotifier conversationNotifier;
    @Mock private UserBlockService userBlocks;
    @Mock private TripMemberRepository tripMembers;

    private MarketplaceChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceChatServiceImpl(repo, chatMapper, hotelRepo, activityRepo, orgRepo, userRepo,
                authorization, history, webSocketService, new ObjectMapper().registerModule(new JavaTimeModule()), businessConfig,
                conversationNotifier, userBlocks, tripMembers);
        when(repo.find(eq(CONVERSATION), any())).thenReturn(Optional.of(tripConversation()));
        when(repo.findParticipants(anyList())).thenReturn(List.of());
        when(tripMembers.findByTripIdAndUserId(TRIP, ME)).thenReturn(Optional.of(tripMember(ME, MemberStatus.ACCEPTED)));
        when(tripMembers.findByTripIdAndUserId(TRIP, FRIEND)).thenReturn(Optional.of(tripMember(FRIEND, MemberStatus.ACCEPTED)));
        when(tripMembers.findByTripIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());
        when(repo.nextSequence(CONVERSATION)).thenReturn(7L);
        when(userBlocks.blockedAmong(any(), anyList())).thenReturn(Set.of());
    }

    @Nested
    @DisplayName("access to a trip thread")
    class Access {

        @Test
        @DisplayName("is decided by the trip, live, not by the mirrored member row")
        void asksTheTrip() {
            service.get(ME, CONVERSATION);

            verify(tripMembers).findByTripIdAndUserId(TRIP, ME);
            verify(repo, never()).canAccess(CONVERSATION, ME);
        }

        @Test
        @DisplayName("is refused to somebody removed from the trip, even while their row survives")
        void refusesSomebodyRemovedFromTheTrip() {
            when(tripMembers.findByTripIdAndUserId(TRIP, FRIEND))
                    .thenReturn(Optional.of(tripMember(FRIEND, MemberStatus.LEFT)));

            assertThatThrownBy(() -> service.get(FRIEND, CONVERSATION)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("is refused to somebody who was never in the trip")
        void refusesStrangers() {
            assertThatThrownBy(() -> service.get(STRANGER, CONVERSATION)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("cannot be obtained by asking the generic start endpoint for a TRIP thread")
        void refusesStartingATripThreadDirectly() {
            StartMarketplaceConversationRequest request = new StartMarketplaceConversationRequest();
            request.setConversationType(MarketplaceConversationType.TRIP);

            assertThatThrownBy(() -> service.start(ME, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("through its trip");
        }
    }

    @Nested
    @DisplayName("what an operator may do with it")
    class OperatorAccess {

        @Test
        @DisplayName("refuses to hand over a trip transcript, whatever reason is given")
        void refusesTripTranscripts() {
            assertThatThrownBy(() -> service.adminMessages(OPERATOR, CONVERSATION, null, 50, "support ticket 12"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("private");
        }

        @Test
        @DisplayName("refuses a person-to-person transcript too")
        void refusesDirectTranscripts() {
            when(repo.find(eq(CONVERSATION), any())).thenReturn(Optional.of(directConversation()));

            assertThatThrownBy(() -> service.adminMessages(OPERATOR, CONVERSATION, null, 50, "a reason"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("refuses to write into one")
        void refusesToSend() {
            SendMarketplaceMessageRequest request = new SendMarketplaceMessageRequest();
            request.setClientMessageId("x");
            request.setContent("hello");

            assertThatThrownBy(() -> service.adminSend(OPERATOR, CONVERSATION, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("refuses to redact a line out of one")
        void refusesToRedact() {
            assertThatThrownBy(() -> service.adminRedact(OPERATOR, CONVERSATION, MESSAGE, "abuse report"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("still reads a booking thread, where a dispute needs a witness")
        void stillReadsBookingThreads() {
            when(repo.find(eq(CONVERSATION), any())).thenReturn(Optional.of(bookingConversation()));
            when(repo.findMessages(eq(CONVERSATION), any(), eq(50))).thenReturn(List.of());

            service.adminMessages(OPERATOR, CONVERSATION, null, 50, "dispute 42");

            verify(history).audit(any(), eq("CONVERSATION"), eq(CONVERSATION),
                    eq("ADMIN_TRANSCRIPT_ACCESSED"), eq(OPERATOR), eq("ADMIN"), eq("dispute 42"), any());
        }
    }

    @Nested
    @DisplayName("sending")
    class Sending {

        @Test
        @DisplayName("keeps a reply inside its own thread")
        void refusesAReplyToAnotherThread() {
            SendMarketplaceMessageRequest request = send("nice");
            request.setReplyToMessageId(MESSAGE);
            when(repo.findMessage(MESSAGE)).thenReturn(Optional.of(
                    MarketplaceMessage.builder().id(MESSAGE).conversationId(OTHER_CONVERSATION).build()));

            assertThatThrownBy(() -> service.send(ME, CONVERSATION, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("same conversation");
        }

        @Test
        @DisplayName("stores the reply link when the quote is in the same thread")
        void keepsTheReplyLink() {
            SendMarketplaceMessageRequest request = send("nice");
            request.setReplyToMessageId(MESSAGE);
            when(repo.findMessage(MESSAGE)).thenReturn(Optional.of(
                    MarketplaceMessage.builder().id(MESSAGE).conversationId(CONVERSATION).build()));

            service.send(ME, CONVERSATION, request);

            ArgumentCaptor<MarketplaceMessage> captor = ArgumentCaptor.forClass(MarketplaceMessage.class);
            verify(repo).insertMessage(captor.capture());
            assertThat(captor.getValue().getReplyToMessageId()).isEqualTo(MESSAGE);
        }

        @Test
        @DisplayName("only honours a mention of somebody actually in the thread")
        void dropsMentionsOfOutsiders() {
            SendMarketplaceMessageRequest request = send("@Linh @ai đó");
            request.setMentionedUserIds(List.of(FRIEND, STRANGER));
            when(repo.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(ME, FRIEND));

            service.send(ME, CONVERSATION, request);

            verify(conversationNotifier).notifyNewMessage(eq(CONVERSATION), eq(ME), any(), eq(List.of(FRIEND)));
        }

        @Test
        @DisplayName("answers a repeated client id with the message already stored")
        void isIdempotentOnTheClientId() {
            MarketplaceMessage already = MarketplaceMessage.builder()
                    .id(MESSAGE).conversationId(CONVERSATION).senderUserId(ME).sequenceNo(3L)
                    .messageType(MarketplaceMessageType.TEXT.name()).content("hello").build();
            when(repo.findMessageByClientId(CONVERSATION, ME, "abc")).thenReturn(Optional.of(already));

            MarketplaceMessageResponse response = service.send(ME, CONVERSATION, send("hello"));

            assertThat(response.getId()).isEqualTo(MESSAGE);
            verify(repo, never()).insertMessage(any());
        }

        @Test
        @DisplayName("refuses to let a client forge a system line")
        void refusesSystemMessagesFromClients() {
            SendMarketplaceMessageRequest request = send("Linh joined the trip");
            request.setMessageType(MarketplaceMessageType.SYSTEM);

            assertThatThrownBy(() -> service.send(ME, CONVERSATION, request)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("the inbox fan-out")
    class InboxFanOut {

        @Test
        @DisplayName("tells every member their inbox row moved, including the sender")
        void announcesToEveryMember() {
            when(repo.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(ME, FRIEND));

            service.send(ME, CONVERSATION, send("di thoi"));

            verify(webSocketService).broadcastToUser(eq(ME), eq("CONVERSATION_UPDATED"), any(), eq(ME));
            verify(webSocketService).broadcastToUser(eq(FRIEND), eq("CONVERSATION_UPDATED"), any(), eq(ME));
        }

        @Test
        @DisplayName("sends the row, not the unread count, which differs per person")
        void sendsOnlyWhatIsTheSameForEverybody() {
            when(repo.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(ME));

            service.send(ME, CONVERSATION, send("di thoi"));

            ArgumentCaptor<java.util.Map<String, Object>> captor =
                    ArgumentCaptor.forClass(java.util.Map.class);
            verify(webSocketService).broadcastToUser(eq(ME), eq("CONVERSATION_UPDATED"), captor.capture(), any());
            assertThat(captor.getValue()).containsKey("conversationId");
            assertThat(captor.getValue()).doesNotContainKey("unreadCount");
            assertThat(captor.getValue()).doesNotContainKey("mutedUntil");
        }

        @Test
        @DisplayName("also announces a deletion, because the last line may have been it")
        void announcesADeletion() {
            when(repo.softDeleteOwnMessage(eq(CONVERSATION), eq(MESSAGE), eq(ME), any())).thenReturn(1);
            when(repo.findActiveMemberIds(CONVERSATION)).thenReturn(List.of(ME, FRIEND));

            service.deleteOwnMessage(ME, CONVERSATION, MESSAGE);

            verify(webSocketService).broadcastToUser(eq(FRIEND), eq("CONVERSATION_UPDATED"), any(), eq(ME));
        }

        @Test
        @DisplayName("does not undo a delivered message when the announcement fails")
        void survivesABrokenAnnouncement() {
            when(repo.findActiveMemberIds(CONVERSATION))
                    .thenThrow(new IllegalStateException("socket is gone"));

            service.send(ME, CONVERSATION, send("di thoi"));

            verify(repo).insertMessage(any());
        }
    }

    @Nested
    @DisplayName("editing and deleting")
    class EditingAndDeleting {

        @Test
        @DisplayName("refuses to delete somebody else's line")
        void refusesDeletingAnothersMessage() {
            when(repo.softDeleteOwnMessage(eq(CONVERSATION), eq(MESSAGE), eq(ME), any())).thenReturn(0);

            assertThatThrownBy(() -> service.deleteOwnMessage(ME, CONVERSATION, MESSAGE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("your own");
        }

        @Test
        @DisplayName("tells the open screens when a line is removed")
        void broadcastsTheDeletion() {
            when(repo.softDeleteOwnMessage(eq(CONVERSATION), eq(MESSAGE), eq(ME), any())).thenReturn(1);

            service.deleteOwnMessage(ME, CONVERSATION, MESSAGE);

            verify(webSocketService).broadcastToConversation(eq(CONVERSATION), eq("MESSAGE_DELETED"), any(), eq(ME));
        }

        @Test
        @DisplayName("refuses to edit a photo, which has no text to correct")
        void refusesEditingAnImage() {
            when(repo.findMessage(MESSAGE)).thenReturn(Optional.of(MarketplaceMessage.builder()
                    .id(MESSAGE).conversationId(CONVERSATION).senderUserId(ME)
                    .messageType(MarketplaceMessageType.IMAGE.name()).build()));
            EditMarketplaceMessageRequest request = new EditMarketplaceMessageRequest();
            request.setContent("corrected");

            assertThatThrownBy(() -> service.editMessage(ME, CONVERSATION, MESSAGE, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("text message");
        }
    }

    @Nested
    @DisplayName("reactions")
    class Reactions {

        @Test
        @DisplayName("are grouped per emoji, counted, and say whether I am one of them")
        void groupsThem() {
            MarketplaceMessage message = MarketplaceMessage.builder()
                    .id(MESSAGE).conversationId(CONVERSATION).senderUserId(FRIEND)
                    .messageType(MarketplaceMessageType.TEXT.name()).content("đi thôi").build();
            when(repo.findMessage(MESSAGE)).thenReturn(Optional.of(message));
            when(repo.findReactions(List.of(MESSAGE))).thenReturn(List.of(
                    reaction("❤️", ME, "Tôi"), reaction("❤️", FRIEND, "Linh"), reaction("🔥", FRIEND, "Linh")));

            MarketplaceMessageResponse response = service.react(ME, CONVERSATION, MESSAGE, "❤️", true);

            assertThat(response.getReactions()).hasSize(2);
            assertThat(response.getReactions().get(0).getEmoji()).isEqualTo("❤️");
            assertThat(response.getReactions().get(0).getCount()).isEqualTo(2);
            assertThat(response.getReactions().get(0).isReactedByMe()).isTrue();
            assertThat(response.getReactions().get(1).isReactedByMe()).isFalse();
        }

        @Test
        @DisplayName("are refused on a line that was deleted")
        void refuseDeletedMessages() {
            when(repo.findMessage(MESSAGE)).thenReturn(Optional.of(MarketplaceMessage.builder()
                    .id(MESSAGE).conversationId(CONVERSATION).deletedAt(LocalDateTime.now()).build()));

            assertThatThrownBy(() -> service.react(ME, CONVERSATION, MESSAGE, "❤️", true))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("muting")
    class Muting {

        @Test
        @DisplayName("without an end date means until I say otherwise, not forever-as-null")
        void mutesIndefinitely() {
            MuteConversationRequest request = new MuteConversationRequest();
            request.setMuted(true);

            service.mute(ME, CONVERSATION, request);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repo).updateMuted(eq(CONVERSATION), eq(ME), captor.capture());
            assertThat(captor.getValue()).isAfter(LocalDateTime.now().plusYears(50));
        }

        @Test
        @DisplayName("is lifted by clearing the date")
        void unmutes() {
            MuteConversationRequest request = new MuteConversationRequest();
            request.setMuted(false);

            service.mute(ME, CONVERSATION, request);

            verify(repo).updateMuted(CONVERSATION, ME, null);
        }
    }

    @Nested
    @DisplayName("the inbox filter")
    class InboxFilter {

        @Test
        @DisplayName("passes the chips through, upper-cased")
        void normalisesTypes() {
            service.listMine(ME, List.of("trip", "direct"), 0, 30);

            verify(repo).findForUser(ME, List.of("TRIP", "DIRECT"), 30, 0);
        }

        @Test
        @DisplayName("refuses a filter that is not a conversation kind")
        void refusesNonsense() {
            assertThatThrownBy(() -> service.listMine(ME, List.of("everything"), 0, 30))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("writes a system line with the event beside the sentence, so clients can translate it")
    void systemMessagesCarryTheirEvent() {
        when(repo.findMessage(any())).thenAnswer(invocation -> Optional.empty());

        service.postSystemMessage(CONVERSATION, "MEMBER_JOINED", FRIEND, "Linh");

        ArgumentCaptor<MarketplaceMessage> captor = ArgumentCaptor.forClass(MarketplaceMessage.class);
        verify(repo).insertMessage(captor.capture());
        MarketplaceMessage stored = captor.getValue();
        assertThat(stored.getMessageType()).isEqualTo(MarketplaceMessageType.SYSTEM.name());
        assertThat(stored.getSenderUserId()).isNull();
        assertThat(stored.getContent()).contains("Linh");
        assertThat(stored.getAttachments()).contains("MEMBER_JOINED").contains(FRIEND.toString());
    }

    private SendMarketplaceMessageRequest send(String content) {
        SendMarketplaceMessageRequest request = new SendMarketplaceMessageRequest();
        request.setClientMessageId("abc");
        request.setContent(content);
        return request;
    }

    private MarketplaceMessageReaction reaction(String emoji, UUID userId, String name) {
        return MarketplaceMessageReaction.builder()
                .messageId(MESSAGE).userId(userId).emoji(emoji).userName(name).build();
    }

    private MarketplaceConversation tripConversation() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.TRIP.name())
                .tripId(TRIP)
                .tripName("Đà Lạt")
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MarketplaceConversation directConversation() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.DIRECT.name())
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MarketplaceConversation bookingConversation() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.HOTEL_BOOKING.name())
                .organizationId(UUID.randomUUID())
                .hotelBookingId(UUID.randomUUID())
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private TripMember tripMember(UUID userId, MemberStatus status) {
        return TripMember.builder()
                .id(UUID.randomUUID()).tripId(TRIP).userId(userId)
                .role(userId.equals(ME) ? MemberRole.OWNER : MemberRole.EDITOR)
                .status(status).build();
    }
}
