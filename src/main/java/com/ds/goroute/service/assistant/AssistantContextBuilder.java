package com.ds.goroute.service.assistant;

import com.ds.goroute.config.AiTripWorkerProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.AssistantHistoryMessage;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplaceMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the worker needs to answer one turn, sent with the job: the question, the recent
 * conversation, and (in a trip chat) the trip's days and activities, so the common questions
 * need no tool call at all.
 */
@Service
@RequiredArgsConstructor
public class AssistantContextBuilder {

    static final int MAX_HISTORY_TEXT = 600;
    static final int MAX_TOOL_CALLS = 6;
    static final int MAX_OUTPUT_TOKENS = 1800;

    private final AssistantRepository repository;
    private final MarketplaceChatRepository chatRepository;
    private final TripRepository trips;
    private final AssistantTripSnapshots tripSnapshots;
    private final BusinessConfigService businessConfig;
    private final AiTripWorkerProperties workerProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> build(java.util.UUID turnId) {
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
        payload.put("limits", Map.of("maxToolCalls", MAX_TOOL_CALLS, "maxOutputTokens", MAX_OUTPUT_TOKENS));
        payload.put("logPayloads", businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_LOG_PAYLOADS));
        return payload;
    }

    private List<Map<String, Object>> history(MarketplaceConversation conversation, long beforeSequence) {
        int limit = businessConfig.getInt(BusinessConfigKey.ASSISTANT_HISTORY_MESSAGES);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssistantHistoryMessage message : repository.findRecentMessages(conversation.getId(), beforeSequence, limit)) {
            String text = text(message);
            if (text.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            boolean fromAssistant = AssistantIdentity.isAssistant(message.getSenderUserId());
            row.put("role", fromAssistant ? "assistant" : "user");
            if (!fromAssistant) row.put("name", message.getSenderName() == null ? "" : message.getSenderName());
            row.put("text", text);
            rows.add(row);
        }
        return rows;
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

    private JsonNode first(String json) {
        try {
            JsonNode node = json == null ? null : objectMapper.readTree(json);
            return node != null && node.isArray() && !node.isEmpty() ? node.get(0) : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }
}
