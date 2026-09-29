package com.ds.goroute.controller;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.WebSocketService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Typing indicators, which are the one thing in a chat that must never be stored.
 *
 * <p>They arrive on the socket and leave on the socket; nothing is written down. A typing
 * notice is only true for the two seconds it is in flight, and a row recording that somebody
 * was typing at 23:40 is a fact about them that outlives its usefulness immediately.
 *
 * <p>At most one notice per person per thread every {@link #TYPING_INTERVAL}: a client sends
 * one per keystroke burst, and every one of them is an access check and a fan-out to the whole
 * thread. Extras are dropped silently; the indicator already on screen covers them. The window
 * is per server instance and in memory, which is all a two-second hint needs.
 */
@Controller
@Slf4j
public class ChatRealtimeController {

    static final Duration TYPING_INTERVAL = Duration.ofMillis(1500);
    /** Bounds memory under a flood of distinct pairs; an evicted pair just gets through once more. */
    private static final long MAX_TRACKED_PAIRS = 100_000;

    private final WebSocketService webSocketService;
    private final MarketplaceConversationAccessService accessService;
    private final Cache<String, Boolean> recentTyping;

    @Autowired
    public ChatRealtimeController(WebSocketService webSocketService, MarketplaceConversationAccessService accessService) {
        this(webSocketService, accessService, Ticker.systemTicker());
    }

    ChatRealtimeController(WebSocketService webSocketService, MarketplaceConversationAccessService accessService,
                           Ticker ticker) {
        this.webSocketService = webSocketService;
        this.accessService = accessService;
        this.recentTyping = Caffeine.newBuilder()
                .expireAfterWrite(TYPING_INTERVAL)
                .maximumSize(MAX_TRACKED_PAIRS)
                .ticker(ticker)
                .build();
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable String conversationId, Principal principal) {
        if (principal == null) return;
        try {
            UUID conversation = UUID.fromString(conversationId);
            UUID userId = UUID.fromString(principal.getName());
            if (recentTyping.asMap().putIfAbsent(userId + ":" + conversation, Boolean.TRUE) != null) return;
            // A SEND is not covered by the subscribe-time check, so it is checked here.
            accessService.requireAccess(conversation, userId);
            webSocketService.broadcastToConversation(conversation, "TYPING",
                    Map.of("userId", userId.toString()), userId);
        } catch (IllegalArgumentException malformed) {
            log.debug("Ignoring a typing notice for {}", conversationId);
        } catch (RuntimeException refused) {
            log.debug("Ignoring a typing notice from someone outside {}", conversationId);
        }
    }
}
