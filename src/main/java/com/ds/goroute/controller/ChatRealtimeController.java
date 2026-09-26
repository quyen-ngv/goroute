package com.ds.goroute.controller;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Typing indicators, which are the one thing in a chat that must never be stored.
 *
 * <p>They arrive on the socket and leave on the socket; nothing is written down. A typing
 * notice is only true for the two seconds it is in flight, and a row recording that somebody
 * was typing at 23:40 is a fact about them that outlives its usefulness immediately.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimeController {

    private final WebSocketService webSocketService;
    private final MarketplaceConversationAccessService accessService;

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable String conversationId, Principal principal) {
        if (principal == null) return;
        try {
            UUID conversation = UUID.fromString(conversationId);
            UUID userId = UUID.fromString(principal.getName());
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
