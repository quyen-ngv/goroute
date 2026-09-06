package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.UUID;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String MARKETPLACE_TOPIC = "/topic/marketplace/conversations/";
    private static final String TRIP_TOPIC = "/topic/trips/";
    private final MarketplaceConversationAccessService marketplaceConversationAccessService;
    private final TripAccessGuard tripAccessGuard;
    private final JwtUtils jwtUtils;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for /topic destinations
        config.enableSimpleBroker("/topic");
        
        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint with SockJS fallback
        registry.addEndpoint("/v1/api/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getDestination() != null
                        && accessor.getDestination().startsWith(MARKETPLACE_TOPIC)) {
                    if (accessor.getUser() == null) {
                        throw new AccessDeniedException("Authentication required for marketplace chat");
                    }
                    try {
                        UUID conversationId = UUID.fromString(accessor.getDestination().substring(MARKETPLACE_TOPIC.length()));
                        UUID userId = UUID.fromString(accessor.getUser().getName());
                        marketplaceConversationAccessService.requireAccess(conversationId,userId);
                    } catch (IllegalArgumentException ex) {
                        throw new AccessDeniedException("Invalid marketplace conversation destination");
                    }
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getDestination() != null
                        && accessor.getDestination().startsWith(TRIP_TOPIC)) {
                    UUID userId = requireUserId(accessor);
                    try {
                        UUID tripId = UUID.fromString(accessor.getDestination().substring(TRIP_TOPIC.length()));
                        tripAccessGuard.requireAccess(tripId, userId);
                    } catch (IllegalArgumentException exception) {
                        throw new AccessDeniedException("Invalid trip destination");
                    }
                }
                return message;
            }
        });
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
}
