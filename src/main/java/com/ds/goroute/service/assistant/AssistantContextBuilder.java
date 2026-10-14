package com.ds.goroute.service.assistant;

import com.ds.goroute.config.AiTripWorkerProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.AssistantHistoryMessage;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ChatShareType;
import com.ds.goroute.type.MarketplaceMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the worker needs to answer one turn, sent with the job: the question, the recent
 * conversation, and (in a trip chat) the trip's days and activities, so the common questions
 * need no tool call at all.
 */
@Service
@RequiredArgsConstructor
public class AssistantContextBuilder {

    static final int MAX_HISTORY_TEXT = 600;
    /**
     * The history window slides in steps of this many messages, not one message per turn: the
     * provider's prompt cache keys on the exact prefix, and a window that drops its oldest line
     * every turn never matches the previous turn's prompt again.
     */
    static final int HISTORY_WINDOW_STEP = 10;
    static final int MAX_TOOL_CALLS = 6;
    static final int MAX_OUTPUT_TOKENS = 1800;

    private final AssistantRepository repository;
    private final MarketplaceChatRepository chatRepository;
    private final TripRepository trips;
    private final PlaceRepository places;
    private final AssistantTripSnapshots tripSnapshots;
    private final BusinessConfigService businessConfig;
    private final AiTripWorkerProperties workerProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> build(UUID turnId) {
        AssistantTurn turn = repository.findTurn(turnId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Assistant turn not found"));
        MarketplaceConversation conversation = chatRepository.find(turn.getConversationId(), null)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Conversation not found"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("turnId", turn.getId().toString());
        payload.put("conversationId", conversation.getId().toString());
        payload.put("conversationType", conversation.getConversationType());
        payload.put("locale", turn.getLocale() == null ? "vi" : turn.getLocale());
        payload.put("user", Map.of("id", turn.getUserId().toString(),
                "name", turn.getUserName() == null ? "" : turn.getUserName()));
        Trip trip = turn.getTripId() == null ? null : trips.findById(turn.getTripId()).orElse(null);
        payload.put("trip", trip == null ? null : tripSnapshots.snapshot(trip));
        long before = repository.findMessageSequence(turn.getTriggerMessageId()).orElse(Long.MAX_VALUE);
        payload.put("history", history(conversation, before));
        payload.put("question", turn.getQuestion() == null ? "" : turn.getQuestion());
        payload.put("callbackBaseUrl", workerProperties.getInternalBaseUrl());
        payload.put("memory", repository.findMemory(conversation.getId()).orElse(null));
        payload.put("limits", Map.of("maxToolCalls", MAX_TOOL_CALLS, "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "webSearch", businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_WEB_SEARCH_ENABLED)));
        payload.put("logPayloads", businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_LOG_PAYLOADS));
        return payload;
    }

    private List<Map<String, Object>> history(MarketplaceConversation conversation, long beforeSequence) {
        int limit = businessConfig.getInt(BusinessConfigKey.ASSISTANT_HISTORY_MESSAGES);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssistantHistoryMessage message : stableWindow(
                repository.findRecentMessages(conversation.getId(), beforeSequence, limit + HISTORY_WINDOW_STEP), limit)) {
            String text = text(message);
            if (text.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            boolean fromAssistant = AssistantIdentity.isAssistant(message.getSenderUserId());
            row.put("role", fromAssistant ? "assistant" : "user");
            if (!fromAssistant) row.put("name", message.getSenderName() == null ? "" : message.getSenderName());
            row.put("text", text);
            List<String> placeIds = fromAssistant ? placeIds(message.getAttachments()) : sharedPlaceIds(message);
            if (!placeIds.isEmpty()) row.put("places", placeIds);
            rows.add(row);
        }
        nameShownPlaces(rows);
        return rows;
    }

    /**
     * The newest {@code limit} messages plus the older ones back to the last sequence number that
     * is a multiple of {@link #HISTORY_WINDOW_STEP}: the window's oldest message then changes only
     * every ten messages, so consecutive turns share a prompt prefix. {@code recent} is newest first.
     */
    static List<AssistantHistoryMessage> stableWindow(List<AssistantHistoryMessage> recent, int limit) {
        if (recent.size() <= limit || limit <= 0) return recent;
        long boundary = recent.get(limit - 1).getSequenceNo() / HISTORY_WINDOW_STEP * HISTORY_WINDOW_STEP;
        List<AssistantHistoryMessage> kept = new ArrayList<>();
        for (AssistantHistoryMessage message : recent) {
            if (kept.size() >= limit && message.getSequenceNo() < boundary) break;
            kept.add(message);
        }
        return kept;
    }

    /** A share has no text: it is described by what it points at, so "this place" makes sense. */
    private String text(AssistantHistoryMessage message) {
        if (MarketplaceMessageType.SHARE.name().equals(message.getMessageType())) {
            JsonNode first = first(message.getAttachments());
            String kind = first.path("shareType").asText("");
            String target = first.path("targetId").asText("");
            return kind.isEmpty() ? "" : "[shared " + kind.toLowerCase() + " " + target + "]";
        }
        String content = message.getContent() == null ? "" : message.getContent().trim();
        return content.length() > MAX_HISTORY_TEXT ? content.substring(0, MAX_HISTORY_TEXT) + "…" : content;
    }

    /** Turns the card ids of earlier answers into {id, name} in one query. */
    @SuppressWarnings("unchecked")
    private void nameShownPlaces(List<Map<String, Object>> rows) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            for (String id : (List<String>) row.getOrDefault("places", List.of())) {
                UUID uuid = AssistantProposalService.uuid(id);
                if (uuid != null) ids.add(uuid);
            }
        }
        if (ids.isEmpty()) return;
        Map<String, String> names = new HashMap<>();
        for (Place place : places.findByIds(new ArrayList<>(ids))) names.put(place.getId().toString(), place.getTitle());
        for (Map<String, Object> row : rows) {
            List<String> shown = (List<String>) row.remove("places");
            if (shown == null) continue;
            List<Map<String, String>> named = shown.stream().filter(names::containsKey)
                    .map(id -> Map.of("id", id, "name", names.get(id))).toList();
            if (!named.isEmpty()) row.put("places", named);
        }
    }

    /**
     * A place a member shared into the chat: named like an earlier card, so "add this place to
     * day 2" resolves to it and Pengoo may show it as a card.
     */
    private List<String> sharedPlaceIds(AssistantHistoryMessage message) {
        if (!MarketplaceMessageType.SHARE.name().equals(message.getMessageType())) return List.of();
        JsonNode first = first(message.getAttachments());
        String target = first.path("targetId").asText("");
        return ChatShareType.PLACE.name().equals(first.path("shareType").asText("")) && !target.isEmpty()
                ? List.of(target) : List.of();
    }

    /** The place cards under an earlier answer, so "the second café" can be resolved to an id. */
    private List<String> placeIds(String json) {
        List<String> ids = new ArrayList<>();
        try {
            JsonNode node = json == null ? null : objectMapper.readTree(json);
            if (node == null || !node.isArray()) return ids;
            for (JsonNode block : node) {
                if ("PLACE".equals(block.path("kind").asText()) && block.hasNonNull("placeId")) {
                    ids.add(block.get("placeId").asText());
                }
            }
        } catch (Exception e) {
            return ids;
        }
        return ids;
    }

    private JsonNode first(String json) {
        try {
            JsonNode node = json == null ? null : objectMapper.readTree(json);
            return node != null && node.isArray() && !node.isEmpty() ? node.get(0) : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }
}
