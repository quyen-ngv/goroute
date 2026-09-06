package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests WebSocket SUBSCRIBE authorization for trip topics.
 * Verifies that only authenticated trip members can subscribe to /topic/trips/{tripId}.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionAuthorizationTest {

    @Mock
    private TripAccessGuard tripAccessGuard;

    @Mock
    private MarketplaceConversationAccessService marketplaceConversationAccessService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private MessageChannel messageChannel;

    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Create interceptor inline to match WebSocketConfig pattern
        interceptor = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getDestination() != null
                        && accessor.getDestination().startsWith("/topic/marketplace/conversations/")) {
                    if (accessor.getUser() == null) {
                        throw new AccessDeniedException("Authentication required for marketplace chat");
                    }
                    try {
                        UUID conversationId = UUID.fromString(accessor.getDestination().substring("/topic/marketplace/conversations/".length()));
                        UUID userId = UUID.fromString(accessor.getUser().getName());
                        marketplaceConversationAccessService.requireAccess(conversationId, userId);
                    } catch (IllegalArgumentException ex) {
                        throw new AccessDeniedException("Invalid marketplace conversation destination");
                    }
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getDestination() != null
                        && accessor.getDestination().startsWith("/topic/trips/")) {
                    UUID userId = requireUserId(accessor);
                    try {
                        UUID tripId = UUID.fromString(accessor.getDestination().substring("/topic/trips/".length()));
                        tripAccessGuard.requireAccess(tripId, userId);
                    } catch (IllegalArgumentException exception) {
                        throw new AccessDeniedException("Invalid trip destination");
                    }
                }
                return message;
            }
        };
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Authentication required for WebSocket connection");
        }
        String token = authorization.substring("Bearer ".length());
        if (!jwtUtils.validateToken(token)) {
            throw new AccessDeniedException("Invalid WebSocket authentication token");
        }
        Claims claims = jwtUtils.getClaimsFromToken(token);
        UUID userId;
        try {
            userId = UUID.fromString(claims.get("userId", String.class));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Invalid WebSocket user");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
    }

    private UUID requireUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("Authentication required for trip updates");
        }
        try {
            return UUID.fromString(accessor.getUser().getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid WebSocket user");
        }
    }

    @Test
    void testAuthenticatedMemberCanSubscribeToTripTopic() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = "valid-jwt-token";

        // Mock JWT validation
        when(jwtUtils.validateToken(token)).thenReturn(true);
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        when(jwtUtils.getClaimsFromToken(token)).thenReturn(claims);

        // Mock trip access - member has access
        doNothing().when(tripAccessGuard).requireAccess(tripId, userId);

        // Create CONNECT message
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        // Create SUBSCRIBE message to trip topic
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/trips/" + tripId);
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // Should not throw
        assertDoesNotThrow(() -> {
            interceptor.preSend(connectMessage, messageChannel);
            interceptor.preSend(subscribeMessage, messageChannel);
        });

        verify(tripAccessGuard).requireAccess(tripId, userId);
    }

    @Test
    void testNonMemberCannotSubscribeToTripTopic() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = "valid-jwt-token";

        // Mock JWT validation
        when(jwtUtils.validateToken(token)).thenReturn(true);
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        when(jwtUtils.getClaimsFromToken(token)).thenReturn(claims);

        // Mock trip access - user is NOT a member
        doThrow(new AccessDeniedException("Access denied"))
                .when(tripAccessGuard).requireAccess(tripId, userId);

        // Create CONNECT message
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        // Create SUBSCRIBE message to trip topic
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/trips/" + tripId);
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // CONNECT should succeed
        assertDoesNotThrow(() -> interceptor.preSend(connectMessage, messageChannel));

        // SUBSCRIBE should fail
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(subscribeMessage, messageChannel)
        );

        verify(tripAccessGuard).requireAccess(tripId, userId);
    }

    @Test
    void testUnauthenticatedUserCannotSubscribeToTripTopic() {
        UUID tripId = UUID.randomUUID();

        // Create SUBSCRIBE message WITHOUT prior CONNECT (no auth)
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/trips/" + tripId);
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // Should fail due to missing authentication
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(subscribeMessage, messageChannel)
        );

        verify(tripAccessGuard, never()).requireAccess(any(), any());
    }

    @Test
    void testInvalidTripIdIsRejected() {
        String token = "valid-jwt-token";
        UUID userId = UUID.randomUUID();

        // Mock JWT validation
        when(jwtUtils.validateToken(token)).thenReturn(true);
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        when(jwtUtils.getClaimsFromToken(token)).thenReturn(claims);

        // Create CONNECT message
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        // Create SUBSCRIBE message with invalid trip ID
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/trips/not-a-valid-uuid");
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // CONNECT succeeds
        assertDoesNotThrow(() -> interceptor.preSend(connectMessage, messageChannel));

        // SUBSCRIBE with invalid UUID should fail
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(subscribeMessage, messageChannel)
        );

        verify(tripAccessGuard, never()).requireAccess(any(), any());
    }

    @Test
    void testMarketplaceTopicIsNotAffected() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = "valid-jwt-token";

        // Mock JWT validation
        when(jwtUtils.validateToken(token)).thenReturn(true);
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        when(jwtUtils.getClaimsFromToken(token)).thenReturn(claims);

        // Mock marketplace access
        doNothing().when(marketplaceConversationAccessService).requireAccess(conversationId, userId);

        // Create CONNECT message
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        // Create SUBSCRIBE message to marketplace topic
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/marketplace/conversations/" + conversationId);
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // Should not throw
        assertDoesNotThrow(() -> {
            interceptor.preSend(connectMessage, messageChannel);
            interceptor.preSend(subscribeMessage, messageChannel);
        });

        verify(marketplaceConversationAccessService).requireAccess(conversationId, userId);
        verify(tripAccessGuard, never()).requireAccess(any(), any());
    }
}
