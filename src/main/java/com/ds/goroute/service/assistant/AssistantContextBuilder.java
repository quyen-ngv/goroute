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
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ChatShareType;
import com.ds.goroute.type.MarketplaceConversationType;
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
    /**
     * Room for a whole trip plan in the answer's JSON (a five-day plan is about 3,000 tokens) plus
     * Claude's thinking, which counts against the same limit; a short answer stops long before it.
     * Was 1800, then 5000: a 3-day plan after 2,040 thinking tokens was cut off mid-JSON (prod
     * 2026-10-28). Only tokens actually written are paid for.
     */
    static final int MAX_OUTPUT_TOKENS = 16000;
    /** Photos read with one question: a receipt, a ticket, a page of notes. */
    static final int MAX_IMAGES = 4;
    /**
     * When the question itself has no photo, an earlier photo of the asker's counts ("add the
     * expense in that picture") if it is one of this many messages just before the question.
     */
    static final int RECENT_IMAGE_MESSAGES = 3;
    /**
     * The worker stops this long before the turn timeout job would fail the turn, so a slow answer
     * ends as the worker's own failure (told to the asker once) rather than racing the job.
     */
    static final int WORKER_TIMEOUT_MARGIN_SECONDS = 15;

    private final AssistantRepository repository;
    private final MarketplaceChatRepository chatRepository;
    private final TripRepository trips;
    private final PlaceRepository places;
    private final AssistantTripSnapshots tripSnapshots;
    private final BusinessConfigService businessConfig;
    private final AiTripWorkerProperties workerProperties;
    private final AssistantAccessService access;
    private final HiddenImageService hiddenImages;
    private final AssistantProposalService proposals;
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
        List<String> images = images(turn, conversation.getId(), before);
        if (!images.isEmpty()) payload.put("images", images);
        payload.put("callbackBaseUrl", workerProperties.getInternalBaseUrl());
        payload.put("memory", repository.findMemory(conversation.getId()).orElse(null));
        // 1:1 threads: the first answer names the thread, and what Pengoo keeps about the asker
        // goes with every question of every thread.
        if (MarketplaceConversationType.ASSISTANT.name().equals(conversation.getConversationType())) {
            if (conversation.getTitle() == null) payload.put("needsTitle", true);
            repository.findProfile(turn.getUserId()).ifPresent(profile -> payload.put("profile", profile));
        }
        int turnTimeout = businessConfig.getInt(BusinessConfigKey.ASSISTANT_TURN_TIMEOUT_SECONDS);
        payload.put("limits", Map.of("maxToolCalls", access.maxToolCalls(turn.getUserId()),
                "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "webSearch", businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_WEB_SEARCH_ENABLED),
                "timeoutSeconds", Math.max(30, turnTimeout - WORKER_TIMEOUT_MARGIN_SECONDS)));
        payload.put("logPayloads", businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_LOG_PAYLOADS));
        return payload;
    }

    private List<Map<String, Object>> history(MarketplaceConversation conversation, long beforeSequence) {
        int limit = businessConfig.getInt(BusinessConfigKey.ASSISTANT_HISTORY_MESSAGES);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<AssistantHistoryMessage> window = stableWindow(
                repository.findRecentMessages(conversation.getId(), beforeSequence, limit + HISTORY_WINDOW_STEP), limit);
        // A plan card holds the itinerary itself (the answer text only sums it up), so Pengoo reads
        // it back to revise it ("swap day 2's café") or to edit the trip it became.
        Map<UUID, String> planNotes = proposals.planNotes(window.stream()
                .filter(message -> AssistantIdentity.isAssistant(message.getSenderUserId()))
                .map(AssistantHistoryMessage::getId).toList());
        for (AssistantHistoryMessage message : window) {
            String text = text(message);
            String planNote = planNotes.get(message.getId());
            if (planNote != null) text = text.isBlank() ? planNote : text + "\n" + planNote;
            if (text.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            boolean fromAssistant = AssistantIdentity.isAssistant(message.getSenderUserId());
            row.put("role", fromAssistant ? "assistant" : "user");
            if (!fromAssistant) row.put("name", message.getSenderName() == null ? "" : message.getSenderName());
            row.put("text", text);
            List<String> placeIds = fromAssistant ? placeIds(message.getAttachments()) : sharedPlaceIds(message);
            if (!placeIds.isEmpty()) row.put("places", placeIds);
            List<Map<String, Object>> mapPlaces = fromAssistant ? mapPlaces(message.getAttachments()) : List.of();
            if (!mapPlaces.isEmpty()) row.put("mapPlaces", mapPlaces);
            rows.add(row);
        }
        nameShownPlaces(rows);
        return rows;
    }

    /**
     * The photos this question is about: the question's own; else those of the message it replies
     * to; else the asker's latest photo message among the few just before the question. Hidden
     * (moderated) photos are left out.
     */
    private List<String> images(AssistantTurn turn, UUID conversationId, long beforeSequence) {
        AssistantHistoryMessage trigger = repository.findMessage(turn.getTriggerMessageId()).orElse(null);
        if (trigger == null) return List.of();
        List<String> own = imageUrls(trigger);
        if (!own.isEmpty()) return own;
        if (trigger.getReplyToMessageId() != null) {
            // A reply always quotes a message of its own thread (the chat service checks it on send).
            List<String> quoted = repository.findMessage(trigger.getReplyToMessageId())
                    .map(this::imageUrls).orElse(List.of());
            if (!quoted.isEmpty()) return quoted;
        }
        List<AssistantHistoryMessage> recent = repository.findRecentMessages(conversationId, beforeSequence,
                RECENT_IMAGE_MESSAGES);
        for (int i = recent.size() - 1; i >= 0; i--) {
            AssistantHistoryMessage message = recent.get(i);
            if (!turn.getUserId().equals(message.getSenderUserId())) continue;
            List<String> urls = imageUrls(message);
            if (!urls.isEmpty()) return urls;
        }
        return List.of();
    }

    private List<String> imageUrls(AssistantHistoryMessage message) {
        if (!MarketplaceMessageType.IMAGE.name().equals(message.getMessageType())) return List.of();
        List<String> urls = new ArrayList<>();
        try {
            JsonNode node = message.getAttachments() == null ? null : objectMapper.readTree(message.getAttachments());
            if (node == null || !node.isArray()) return urls;
            for (JsonNode attachment : node) {
                String url = attachment.path("url").asText("");
                if (url.isBlank() || hiddenImages.isHidden(url)) continue;
                urls.add(url);
                if (urls.size() >= MAX_IMAGES) break;
            }
        } catch (Exception e) {
            return urls;
        }
        return urls;
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
        String text = content.length() > MAX_HISTORY_TEXT ? content.substring(0, MAX_HISTORY_TEXT) + "…" : content;
        if (MarketplaceMessageType.IMAGE.name().equals(message.getMessageType())) {
            // The photos themselves go only with the question they belong to; earlier ones are a marker.
            return text.isEmpty() ? "[photo]" : text + " [photo]";
        }
        return text;
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

    /**
     * The map cards under an earlier answer, whole: a map place has no catalogue row to look up,
     * so "add the second one to day 2" needs its position from here.
     */
    private List<Map<String, Object>> mapPlaces(String json) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            JsonNode node = json == null ? null : objectMapper.readTree(json);
            if (node == null || !node.isArray()) return rows;
            for (JsonNode block : node) {
                if (!"MAP_PLACE".equals(block.path("kind").asText()) || !block.hasNonNull("providerPlaceId")
                        || !block.path("lat").isNumber() || !block.path("lng").isNumber()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "map:" + block.path("provider").asText() + ":" + block.path("providerPlaceId").asText());
                row.put("name", block.path("name").asText(""));
                if (block.hasNonNull("address")) row.put("address", block.get("address").asText());
                row.put("lat", block.get("lat").decimalValue());
                row.put("lng", block.get("lng").decimalValue());
                rows.add(row);
            }
        } catch (Exception e) {
            return rows;
        }
        return rows;
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
