package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.AssistantStepRequest;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Place;
import com.ds.goroute.event.AssistantTurnDispatchEvent;
import com.ds.goroute.event.ChatMessageCreatedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.WebSocketService;
import com.ds.goroute.service.marketplace.ChatShareValidator;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.AssistantTurnStatus;
import com.ds.goroute.type.ChatShareType;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MarketplaceMessageType;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The life of one question to Pengoo, from the message that asks it to the message that answers.
 *
 * <ul>
 *   <li>A trip chat asks by mentioning Pengoo or replying to one of its messages; the 1:1 chat
 *       asks with every message.</li>
 *   <li>One turn per conversation runs at a time; later questions wait (QUEUED) in order.</li>
 *   <li>A question counts against the asker's daily allowance when it is accepted; a failure
 *       takes it back, so a broken answer is never paid for.</li>
 *   <li>Every state change is pushed to the thread as {@code ASSISTANT_TURN} so the app can show
 *       what Pengoo is doing right now.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantTurnService {

    static final String EVENT_TURN = "ASSISTANT_TURN";
    static final int MAX_PLACE_CARDS = 8;
    static final int MAX_ITEM_CARDS = 6;
    static final int MAX_SUGGESTIONS = 4;
    static final int MAX_MEMORY_CHARS = 1500;
    private static final Pattern MENTION = Pattern.compile("(?i)@pengoo\\b[:,]?\\s*");

    private final AssistantRepository repository;
    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final AssistantAccessService access;
    private final AssistantTurnSnapshots snapshots;
    private final AssistantProposalService proposals;
    private final AssistantActionService actions;
    private final PlaceRepository places;
    private final ChatShareValidator shareValidator;
    private final TextModerationService moderation;
    private final WebSocketService webSocketService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    /** Decides whether a stored message asks Pengoo something, and if so queues or starts a turn. */
    @Transactional
    public void onMessage(ChatMessageCreatedEvent message) {
        UUID askerId = message.senderUserId();
        if (askerId == null || AssistantIdentity.isAssistant(askerId)) return;
        if (!MarketplaceMessageType.TEXT.name().equals(message.messageType())) return;
        MarketplaceConversation conversation = chatRepository.find(message.conversationId(), null).orElse(null);
        if (conversation == null || !asksAssistant(conversation, message)) return;
        if (!access.isAvailable(askerId)) return;

        // Serialises turns of one conversation: two mentions at once must not both start running.
        chatRepository.lock(conversation.getId());
        LocalDateTime now = access.now();
        String question = question(message.content());
        String language = message.language();
        if (access.usedToday(askerId) >= access.dailyLimit(askerId)) {
            AssistantTurn rejected = newTurn(conversation, message, AssistantTurnStatus.REJECTED, false, now, question, language);
            repository.insertTurn(rejected);
            int limit = access.dailyLimit(askerId);
            chatService.postAssistantMessage(conversation.getId(), AssistantNotices.quotaExhausted(language, limit),
                    List.of(Map.of("kind", "NOTICE", "code", "QUOTA_EXHAUSTED", "limit", limit)),
                    replyTarget(conversation, message.messageId()), askerId, "turn:" + rejected.getId());
            return;
        }
        boolean busy = repository.hasOpenTurn(conversation.getId());
        AssistantTurn turn = newTurn(conversation, message,
                busy ? AssistantTurnStatus.QUEUED : AssistantTurnStatus.RUNNING, true, now, question, language);
        repository.insertTurn(turn);
        broadcastAfterCommit(turn.getId());
        if (!busy) events.publishEvent(new AssistantTurnDispatchEvent(turn.getId()));
    }

    /** A step the worker reports; ignored once the turn is over (a late step after a timeout). */
    @Transactional
    public void appendStep(UUID turnId, AssistantStepRequest step) {
        AssistantTurn turn = repository.findTurn(turnId).orElseThrow(this::notFound);
        if (!AssistantTurnStatus.RUNNING.name().equals(turn.getStatus())) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", step.getKey());
        row.put("params", cleanParams(step.getParams()));
        row.put("text", allowed(step.getText()));
        row.put("at", access.now().toString());
        if (repository.appendTurnStep(turnId, json(List.of(row)))) broadcastAfterCommit(turnId);
    }

    /**
     * Posts the answer. Idempotent: a second reply for a completed turn returns the first answer's
     * message id; a reply for a turn already failed (timed out) is dropped and returns null.
     */
    @Transactional
    public UUID complete(UUID turnId, AssistantReplyRequest reply) {
        AssistantTurn turn = repository.lockTurn(turnId).orElseThrow(this::notFound);
        if (AssistantTurnStatus.COMPLETED.name().equals(turn.getStatus())) return turn.getReplyMessageId();
        if (!AssistantTurnStatus.RUNNING.name().equals(turn.getStatus())) return null;
        MarketplaceConversation conversation = chatRepository.find(turn.getConversationId(), null)
                .orElseThrow(this::notFound);

        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(thinkingBlock(turn));
        blocks.addAll(actions.destinationBlocks(reply.getDestinations(), this::allowed));
        placeBlocks(reply.getPlaces()).forEach(blocks::add);
        blocks.addAll(cardBlocks(turn, reply.getCards()));
        UUID conversationTripId = MarketplaceConversationType.TRIP.name().equals(conversation.getConversationType())
                ? conversation.getTripId() : null;
        if (reply.getProposals() != null) reply.getProposals().forEach(draft -> draft.setSummary(allowed(draft.getSummary())));
        for (UUID proposalId : proposals.createFromReply(turn, conversationTripId, reply.getProposals())) {
            blocks.add(Map.of("kind", "PROPOSAL", "proposalId", proposalId.toString()));
        }
        List<String> suggestions = suggestions(reply.getSuggestions()).stream()
                .map(this::allowed).filter(java.util.Objects::nonNull).toList();
        blocks.addAll(actions.blocks(turn, reply.getActions(), this::allowed));
        if (!suggestions.isEmpty()) blocks.add(Map.of("kind", "SUGGESTIONS", "items", suggestions));

        MarketplaceMessageResponse message = chatService.postAssistantMessage(conversation.getId(),
                answerText(reply.getMarkdown(), turn.getLocale()), blocks,
                replyTarget(conversation, turn.getTriggerMessageId()), turn.getUserId(), "turn:" + turn.getId());
        repository.completeTurn(turnId, message.getId(), reply.getInputTokens(), reply.getOutputTokens(), access.now());
        repository.attachProposalsToMessage(turnId, message.getId());
        saveMemory(turn, reply.getMemory());
        broadcastAfterCommit(turnId);
        startNext(conversation.getId());
        return message.getId();
    }

    /** Ends an open turn with an apology to the asker; the question is not counted. */
    @Transactional
    public void fail(UUID turnId, String error) {
        AssistantTurn turn = repository.lockTurn(turnId).orElse(null);
        if (turn == null || !AssistantTurnStatus.valueOf(turn.getStatus()).isOpen()) return;
        MarketplaceConversation conversation = chatRepository.find(turn.getConversationId(), null).orElse(null);
        UUID replyId = null;
        if (conversation != null) {
            MarketplaceMessageResponse message = chatService.postAssistantMessage(conversation.getId(),
                    AssistantNotices.failed(turn.getLocale()),
                    List.of(Map.of("kind", "NOTICE", "code", "FAILED")),
                    replyTarget(conversation, turn.getTriggerMessageId()), turn.getUserId(), "turn:" + turn.getId());
            replyId = message.getId();
        }
        repository.failTurn(turnId, truncate(error, 1000), replyId, access.now());
        log.info("Assistant turn {} failed: {}", turnId, truncate(error, 200));
        broadcastAfterCommit(turnId);
        if (conversation != null) startNext(conversation.getId());
    }

    /** Running turns older than the timeout, and queued ones that waited three times as long. */
    @Transactional(readOnly = true)
    public List<UUID> staleTurnIds(Duration timeout) {
        LocalDateTime now = access.now();
        return repository.findStaleTurnIds(now.minus(timeout), now.minus(timeout.multipliedBy(3)), 50);
    }

    /** The turn exists and is still being worked on: the gate for every worker callback. */
    @Transactional(readOnly = true)
    public AssistantTurn requireRunning(UUID turnId) {
        AssistantTurn turn = repository.findTurn(turnId).orElseThrow(this::notFound);
        if (!AssistantTurnStatus.RUNNING.name().equals(turn.getStatus())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Assistant turn is not running");
        }
        return turn;
    }

    private void startNext(UUID conversationId) {
        if (repository.findRunningTurn(conversationId).isPresent()) return;
        repository.findNextQueuedTurn(conversationId).ifPresent(next -> {
            if (repository.markTurnRunning(next.getId(), access.now())) {
                broadcastAfterCommit(next.getId());
                events.publishEvent(new AssistantTurnDispatchEvent(next.getId()));
            }
        });
    }

    private boolean asksAssistant(MarketplaceConversation conversation, ChatMessageCreatedEvent message) {
        String type = conversation.getConversationType();
        if (MarketplaceConversationType.ASSISTANT.name().equals(type)) {
            return message.senderUserId().equals(conversation.getAssistantUserId());
        }
        if (!MarketplaceConversationType.TRIP.name().equals(type)) return false;
        if (message.requestedMentionIds() != null
                && message.requestedMentionIds().contains(AssistantIdentity.BOT_USER_ID)) {
            return true;
        }
        return message.replyToMessageId() != null && repository.findMessageSender(message.replyToMessageId())
                .map(AssistantIdentity::isAssistant)
                .orElse(false);
    }

    /** In a group the answer quotes the question; the 1:1 chat is a plain back-and-forth. */
    private static UUID replyTarget(MarketplaceConversation conversation, UUID triggerMessageId) {
        return MarketplaceConversationType.TRIP.name().equals(conversation.getConversationType()) ? triggerMessageId : null;
    }

    private AssistantTurn newTurn(MarketplaceConversation conversation, ChatMessageCreatedEvent message,
                                  AssistantTurnStatus status, boolean counted, LocalDateTime now,
                                  String question, String language) {
        return AssistantTurn.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .tripId(conversation.getTripId())
                .userId(message.senderUserId())
                .triggerMessageId(message.messageId())
                .status(status.name())
                .counted(counted)
                .locale(language)
                .question(question)
                .steps("[]")
                .startedAt(status == AssistantTurnStatus.RUNNING ? now : null)
                .createdAt(now)
                .build();
    }

    static String question(String content) {
        if (content == null) return "";
        String text = MENTION.matcher(content).replaceAll("").trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    private Map<String, Object> thinkingBlock(AssistantTurn turn) {
        long durationMs = turn.getStartedAt() == null ? 0
                : Math.max(0, Duration.between(turn.getStartedAt(), access.now()).toMillis());
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", "THINKING");
        block.put("turnId", turn.getId().toString());
        block.put("durationMs", durationMs);
        block.put("steps", snapshots.steps(turn.getSteps()));
        return block;
    }

    /** Only places that exist and are public become cards; the model never invents one. */
    private List<Map<String, Object>> placeBlocks(List<AssistantReplyRequest.PlaceRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        Map<UUID, String> notes = new LinkedHashMap<>();
        for (AssistantReplyRequest.PlaceRef ref : refs) {
            UUID placeId = AssistantProposalService.uuid(ref.getPlaceId());
            if (placeId != null && !notes.containsKey(placeId) && notes.size() < MAX_PLACE_CARDS) {
                notes.put(placeId, allowed(ref.getNote()));
            }
        }
        if (notes.isEmpty()) return List.of();
        Set<UUID> visible = new LinkedHashSet<>();
        for (Place place : places.findByIds(new ArrayList<>(notes.keySet()))) {
            if (place.getVisibilityStatus() == null || place.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE) {
                visible.add(place.getId());
            }
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        notes.forEach((placeId, note) -> {
            if (!visible.contains(placeId)) return;
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("kind", "PLACE");
            block.put("placeId", placeId.toString());
            if (note != null && !note.isEmpty()) block.put("note", note);
            blocks.add(block);
        });
        return blocks;
    }

    /**
     * Cards of public items (shared trips, events, meetups, check-ins, dishes), kept only when
     * the asker could share them into a chat; readers load each live, like a person's share.
     */
    private List<Map<String, Object>> cardBlocks(AssistantTurn turn, List<AssistantReplyRequest.CardRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<Map<String, Object>> blocks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AssistantReplyRequest.CardRef ref : refs) {
            if (blocks.size() >= MAX_ITEM_CARDS) break;
            ChatShareType type = shareType(ref.getType());
            UUID targetId = AssistantProposalService.uuid(ref.getId());
            if (type == null || targetId == null || !seen.add(type + ":" + targetId)) continue;
            String citySlug = type == ChatShareType.FOOD ? ref.getCitySlug() : null;
            if (!shareValidator.isShareable(turn.getUserId(), type, targetId, citySlug)) continue;
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("kind", "CARD");
            block.put("shareType", type.name());
            block.put("targetId", targetId.toString());
            if (citySlug != null) block.put("citySlug", citySlug);
            blocks.add(block);
        }
        return blocks;
    }

    private static ChatShareType shareType(String value) {
        if (value == null) return null;
        try {
            ChatShareType type = ChatShareType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            // Places have their own cards with "add to trip".
            return type == ChatShareType.PLACE ? null : type;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The worker's notes replace the old ones; never shown to anyone, so only trimmed and capped. */
    private void saveMemory(AssistantTurn turn, String memory) {
        if (memory == null || memory.isBlank()) return;
        repository.saveMemory(turn.getConversationId(), truncate(memory.trim(), MAX_MEMORY_CHARS), turn.getId(), access.now());
    }

    private static List<String> suggestions(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** The answer text, checked by the chat filter like anything a person writes. */
    private String answerText(String markdown, String language) {
        String text = markdown == null ? "" : markdown.trim();
        if (text.isEmpty()) return AssistantNotices.emptyAnswer(language);
        ModerationVerdict verdict = moderation.evaluate(ModeratedContentType.CHAT_MESSAGE, "assistant.answer",
                ModerationVisibility.CHAT, text, AssistantIdentity.BOT_USER_ID);
        return verdict.blocks() ? AssistantNotices.cannotAnswer(language) : text;
    }

    /** A short piece of Pengoo's text that people will see, or null when empty or blocked by the chat filter. */
    private String allowed(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        ModerationVerdict verdict = moderation.evaluate(ModeratedContentType.CHAT_MESSAGE, "assistant.text",
                ModerationVisibility.CHAT, trimmed, AssistantIdentity.BOT_USER_ID);
        return verdict.blocks() ? null : trimmed;
    }

    /** Step params are display values only: short scalars, nothing nested. */
    private static Map<String, Object> cleanParams(Map<String, Object> params) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (params == null) return out;
        params.forEach((key, value) -> {
            if (key == null || key.length() > 24 || value == null) return;
            if (value instanceof Number || value instanceof Boolean) out.put(key, value);
            else if (value instanceof String s && !s.isBlank()) out.put(key, truncate(s.trim(), 80));
        });
        return out;
    }

    private void broadcastAfterCommit(UUID turnId) {
        Runnable send = () -> repository.findTurn(turnId).ifPresent(turn -> webSocketService.broadcastToConversation(
                turn.getConversationId(), EVENT_TURN, snapshots.asEvent(turn), AssistantIdentity.BOT_USER_ID));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        send.run();
                    } catch (RuntimeException e) {
                        log.warn("Could not announce assistant turn {}: {}", turnId, e.getMessage());
                    }
                }
            });
        } else {
            send.run();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Assistant turn not found");
    }
}
