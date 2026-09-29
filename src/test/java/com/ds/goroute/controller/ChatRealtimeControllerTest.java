package com.ds.goroute.controller;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.WebSocketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Typing notices")
class ChatRealtimeControllerTest {

    private final WebSocketService sockets = mock(WebSocketService.class);
    private final AtomicLong nanos = new AtomicLong(1);
    private final ChatRealtimeController controller = new ChatRealtimeController(
            sockets, mock(MarketplaceConversationAccessService.class), nanos::get);

    @Test
    @DisplayName("go out at most once per person and thread every 1.5 s; the rest are dropped")
    void rateLimited() {
        UUID user = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID otherConversation = UUID.randomUUID();
        Principal principal = user::toString;

        controller.typing(conversation.toString(), principal);
        controller.typing(conversation.toString(), principal);
        controller.typing(otherConversation.toString(), principal);
        advance(Duration.ofMillis(1000));
        controller.typing(conversation.toString(), principal);
        verify(sockets, times(1)).broadcastToConversation(eq(conversation), eq("TYPING"), any(), eq(user));
        verify(sockets, times(1)).broadcastToConversation(eq(otherConversation), eq("TYPING"), any(), eq(user));

        advance(Duration.ofMillis(600));
        controller.typing(conversation.toString(), principal);
        verify(sockets, times(2)).broadcastToConversation(eq(conversation), eq("TYPING"), any(), eq(user));
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
