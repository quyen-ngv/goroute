package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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
    @Mock
    private ChannelRegistration channelRegistration;
    @Mock
    private ObjectProvider<org.springframework.messaging.simp.user.SimpUserRegistry> simpUserRegistryProvider;

    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketConfig config = new WebSocketConfig(
                marketplaceConversationAccessService,
                tripAccessGuard,
                jwtUtils,
                simpUserRegistryProvider);
        config.configureClientInboundChannel(channelRegistration);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(channelRegistration).interceptors(captor.capture());
        interceptor = captor.getValue();
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
        when(tripAccessGuard.requireAccess(tripId, userId)).thenReturn(null);

        // Create CONNECT message
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        // Create SUBSCRIBE message to trip topic
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/trips/" + tripId);
        subscribeAccessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
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
        subscribeAccessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
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
        subscribeAccessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
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
