package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Place;
import com.ds.goroute.event.AssistantTurnDispatchEvent;
import com.ds.goroute.event.ChatMessageCreatedEvent;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.WebSocketService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.AssistantTurnStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.PlaceVisibilityStatus;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantTurnServiceTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID ASKER = UUID.randomUUID();
    private static final UUID MESSAGE = UUID.randomUUID();
    private static final UUID BOT = AssistantIdentity.BOT_USER_ID;

    @Mock private AssistantRepository repository;
    @Mock private MarketplaceChatRepository chatRepository;
    @Mock private MarketplaceChatService chatService;
    @Mock private AssistantAccessService access;
    @Mock private AssistantProposalService proposals;
    @Mock private PlaceRepository places;
    @Mock private TextModerationService moderation;
    @Mock private WebSocketService webSocketService;
    @Mock private ApplicationEventPublisher events;

    private AssistantTurnService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new AssistantTurnService(repository, chatRepository, chatService, access,
                new AssistantTurnSnapshots(mapper), proposals, places, moderation, webSocketService, events, mapper);
        when(chatRepository.find(eq(CONVERSATION), any())).thenReturn(Optional.of(tripConversation()));
        when(access.isAvailable(ASKER)).thenReturn(true);
        when(access.dailyLimit(ASKER)).thenReturn(5);
        when(access.usedToday(ASKER)).thenReturn(1);
        when(access.now()).thenReturn(LocalDateTime.of(2026, 10, 12, 10, 0));
        when(moderation.evaluate(any(), anyString(), any(), anyString(), any())).thenReturn(ModerationVerdict.allowed());
        when(chatService.postAssistantMessage(any(), any(), anyList(), any(), any(), anyString()))
                .thenReturn(MarketplaceMessageResponse.builder().id(UUID.randomUUID()).build());
    }

    private static MarketplaceConversation tripConversation() {
        return MarketplaceConversation.builder().id(CONVERSATION).tripId(TRIP)
                .conversationType(MarketplaceConversationType.TRIP.name()).build();
    }

    private static ChatMessageCreatedEvent message(List<UUID> mentions, UUID replyTo) {
        return new ChatMessageCreatedEvent(CONVERSATION, MESSAGE, ASKER, "TEXT", "@Pengoo tối nay ăn gì?",
                mentions, replyTo, "vi");
    }

    @Nested
    @DisplayName("deciding whether a message asks Pengoo")
    class Trigger {

        @Test
        void aMentionStartsATurnAndSendsItToTheWorker() {
            service.onMessage(message(List.of(BOT), null));

            ArgumentCaptor<AssistantTurn> turn = ArgumentCaptor.forClass(AssistantTurn.class);
            verify(repository).insertTurn(turn.capture());
            assertThat(turn.getValue().getStatus()).isEqualTo(AssistantTurnStatus.RUNNING.name());
            assertThat(turn.getValue().isCounted()).isTrue();
            assertThat(turn.getValue().getQuestion()).isEqualTo("tối nay ăn gì?");
            assertThat(turn.getValue().getTripId()).isEqualTo(TRIP);
            verify(chatRepository).lock(CONVERSATION);
            verify(events).publishEvent(any(AssistantTurnDispatchEvent.class));
        }

        @Test
        void aReplyToPengooIsAQuestionToo() {
            UUID pengooMessage = UUID.randomUUID();
            when(repository.findMessageSender(pengooMessage)).thenReturn(Optional.of(BOT));

            service.onMessage(message(List.of(), pengooMessage));

            verify(repository).insertTurn(any());
        }

        @Test
        void aMessageWithoutMentionIsIgnored() {
            service.onMessage(message(List.of(UUID.randomUUID()), null));

            verify(repository, never()).insertTurn(any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        void someoneWithoutAccessIsIgnoredSilently() {
            when(access.isAvailable(ASKER)).thenReturn(false);

            service.onMessage(message(List.of(BOT), null));

            verify(repository, never()).insertTurn(any());
            verify(chatService, never()).postAssistantMessage(any(), any(), anyList(), any(), any(), anyString());
        }

        @Test
        void aSecondQuestionWaitsForTheFirst() {
            when(repository.hasOpenTurn(CONVERSATION)).thenReturn(true);

            service.onMessage(message(List.of(BOT), null));

            ArgumentCaptor<AssistantTurn> turn = ArgumentCaptor.forClass(AssistantTurn.class);
            verify(repository).insertTurn(turn.capture());
            assertThat(turn.getValue().getStatus()).isEqualTo(AssistantTurnStatus.QUEUED.name());
            verify(events, never()).publishEvent(any());
        }

        @Test
        void anExhaustedAllowanceIsAnsweredWithoutTheModel() {
            when(access.usedToday(ASKER)).thenReturn(5);

            service.onMessage(message(List.of(BOT), null));

            ArgumentCaptor<AssistantTurn> turn = ArgumentCaptor.forClass(AssistantTurn.class);
            verify(repository).insertTurn(turn.capture());
            assertThat(turn.getValue().getStatus()).isEqualTo(AssistantTurnStatus.REJECTED.name());
            assertThat(turn.getValue().isCounted()).isFalse();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> blocks = ArgumentCaptor.forClass(List.class);
            verify(chatService).postAssistantMessage(eq(CONVERSATION), anyString(), blocks.capture(), eq(MESSAGE),
                    eq(ASKER), anyString());
            assertThat(blocks.getValue().get(0)).containsEntry("code", "QUOTA_EXHAUSTED").containsEntry("limit", 5);
            verify(events, never()).publishEvent(any());
        }

        @Test
        void pengooNeverAnswersItself() {
            service.onMessage(new ChatMessageCreatedEvent(CONVERSATION, MESSAGE, BOT, "TEXT", "@Pengoo",
                    List.of(BOT), null, "vi"));

            verify(repository, never()).insertTurn(any());
        }
    }

    @Nested
    @DisplayName("posting the answer")
    class Completion {

        private final UUID turnId = UUID.randomUUID();

        @BeforeEach
        void runningTurn() {
            AssistantTurn turn = AssistantTurn.builder().id(turnId).conversationId(CONVERSATION).tripId(TRIP)
                    .userId(ASKER).triggerMessageId(MESSAGE).status(AssistantTurnStatus.RUNNING.name())
                    .locale("vi").steps("[{\"key\":\"search_places\",\"params\":{\"query\":\"bún\"}}]")
                    .startedAt(LocalDateTime.of(2026, 10, 12, 9, 59, 50)).build();
            when(repository.lockTurn(turnId)).thenReturn(Optional.of(turn));
            when(repository.findTurn(turnId)).thenReturn(Optional.of(turn));
        }

        @Test
        void onlyRealPublicPlacesBecomeCardsAndTheQuestionIsQuoted() {
            UUID real = UUID.randomUUID();
            UUID hidden = UUID.randomUUID();
            when(places.findByIds(anyList())).thenReturn(List.of(
                    Place.builder().id(real).visibilityStatus(PlaceVisibilityStatus.ACTIVE).build(),
                    Place.builder().id(hidden).visibilityStatus(PlaceVisibilityStatus.INACTIVE).build()));
            AssistantReplyRequest reply = new AssistantReplyRequest();
            reply.setMarkdown("Thử **Bún chả cá** nhé");
            reply.setPlaces(List.of(ref(real, "Gần"), ref(hidden, "x"), ref(UUID.randomUUID(), "bịa")));
            reply.setSuggestions(List.of("Cà phê gần đó?"));

            service.complete(turnId, reply);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> blocks = ArgumentCaptor.forClass(List.class);
            verify(chatService).postAssistantMessage(eq(CONVERSATION), eq("Thử **Bún chả cá** nhé"), blocks.capture(),
                    eq(MESSAGE), eq(ASKER), eq("turn:" + turnId));
            List<Map<String, Object>> sent = blocks.getValue();
            assertThat(sent.get(0)).containsEntry("kind", "THINKING").containsEntry("durationMs", 10_000L);
            assertThat(sent).filteredOn(block -> "PLACE".equals(block.get("kind")))
                    .extracting(block -> block.get("placeId")).containsExactly(real.toString());
            assertThat(sent).anySatisfy(block -> assertThat(block).containsEntry("kind", "SUGGESTIONS"));
            verify(repository).completeTurn(eq(turnId), any(), any(), any(), any());
        }

        @Test
        void aBlockedAnswerIsReplacedNotPosted() {
            when(moderation.evaluate(any(), anyString(), any(), anyString(), any()))
                    .thenReturn(ModerationVerdict.of(ModerationAction.BLOCK, null, null, "x"));
            AssistantReplyRequest reply = new AssistantReplyRequest();
            reply.setMarkdown("something bad");

            service.complete(turnId, reply);

            verify(chatService).postAssistantMessage(eq(CONVERSATION), eq("Pengoo không thể trả lời nội dung này."),
                    anyList(), any(), any(), anyString());
        }

        @Test
        void aFailureApologisesAndUncountsTheQuestion() {
            service.fail(turnId, "timeout");

            verify(chatService).postAssistantMessage(eq(CONVERSATION), anyString(), anyList(), eq(MESSAGE), eq(ASKER),
                    eq("turn:" + turnId));
            verify(repository).failTurn(eq(turnId), eq("timeout"), any(), any());
        }

        private AssistantReplyRequest.PlaceRef ref(UUID id, String note) {
            AssistantReplyRequest.PlaceRef ref = new AssistantReplyRequest.PlaceRef();
            ref.setPlaceId(id.toString());
            ref.setNote(note);
            return ref;
        }
    }

    @Test
    void theMentionIsStrippedFromTheQuestion() {
        assertThat(AssistantTurnService.question("@Pengoo, cho mình ý tưởng")).isEqualTo("cho mình ý tưởng");
        assertThat(AssistantTurnService.question("hỏi @pengoo: mấy giờ?")).isEqualTo("hỏi mấy giờ?");
    }
}
