package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
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
    private static final String USER_TOPIC_PREFIX = "/topic/users/";
    private static final String USER_TOPIC_SUFFIX = "/chat";
    private final MarketplaceConversationAccessService marketplaceConversationAccessService;
    private final TripAccessGuard tripAccessGuard;
    private final JwtUtils jwtUtils;
    /** Lazily resolved because the broker creates this registry while consuming this configurer. */
    private final ObjectProvider<SimpUserRegistry> simpUserRegistryProvider;

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
                    return authenticate(message, accessor);
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
                        && accessor.getDestination().startsWith(USER_TOPIC_PREFIX)) {
                    UUID userId = requireUserId(accessor);
                    UUID owner = parseUserDestination(accessor.getDestination());
                    if (owner == null || !owner.equals(userId)) {
                        throw new AccessDeniedException("You can only follow your own inbox");
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

    /**
     * The simple broker fans a topic message out to every session that subscribed to it.
     * Checking only SUBSCRIBE is not enough: a member can be removed or demoted while a
     * connection is still open. Re-check the current trip grant on the per-session outbound
     * message and fail closed when the broker cannot resolve its authenticated session.
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (!SimpMessageType.MESSAGE.equals(accessor.getMessageType())
                        && !StompCommand.MESSAGE.equals(accessor.getCommand())) {
                    return message;
                }
                if (isUserDestination(accessor.getDestination())) {
                    UUID owner = parseUserDestination(accessor.getDestination());
                    UUID subscriber = resolveOutboundUserId(accessor);
                    return owner != null && owner.equals(subscriber) ? message : null;
                }
                if (isConversationDestination(accessor.getDestination())) {
                    UUID conversationId = parseConversationDestination(accessor.getDestination());
                    UUID subscriber = resolveOutboundUserId(accessor);
                    if (conversationId == null || subscriber == null) {
                        return null;
                    }
                    try {
                        marketplaceConversationAccessService.requireAccess(conversationId, subscriber);
                        return message;
                    } catch (RuntimeException exception) {
                        // Membership changed after SUBSCRIBE; the thread is no longer theirs.
                        return null;
                    }
                }
                if (!isTripDestination(accessor.getDestination())) {
                    return message;
                }
                UUID tripId = parseTripDestination(accessor.getDestination());
                if (tripId == null) {
                    return null;
                }

                UUID userId = resolveOutboundUserId(accessor);
                if (userId == null) {
                    return null;
                }
                try {
                    tripAccessGuard.requireAccess(tripId, userId);
                    return message;
                } catch (RuntimeException exception) {
                    // Do not leak a trip event when membership changed after SUBSCRIBE.
                    return null;
                }
            }
        });
    }

    private boolean isUserDestination(String destination) {
        return destination != null
                && destination.startsWith(USER_TOPIC_PREFIX)
                && destination.endsWith(USER_TOPIC_SUFFIX);
    }

    /** The owner named by {@code /topic/users/<id>/chat}, or null when it is not one. */
    private UUID parseUserDestination(String destination) {
        if (!isUserDestination(destination)) return null;
        String id = destination.substring(
                USER_TOPIC_PREFIX.length(),
                destination.length() - USER_TOPIC_SUFFIX.length());
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isConversationDestination(String destination) {
        return destination != null && destination.startsWith(MARKETPLACE_TOPIC);
    }

    private UUID parseConversationDestination(String destination) {
        try {
            return UUID.fromString(destination.substring(MARKETPLACE_TOPIC.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Message<?> authenticate(Message<?> message, StompHeaderAccessor accessor) {
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
        // StompHeaderAccessor.wrap can expose a mutable view, but returning the original
        // message does not reliably carry the principal into the broker's session state.
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
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

    private boolean isTripDestination(String destination) {
        return destination != null && destination.startsWith(TRIP_TOPIC);
    }

    private UUID parseTripDestination(String destination) {
        if (!isTripDestination(destination)) {
            return null;
        }
        String tripId = destination.substring(TRIP_TOPIC.length());
        if (tripId.isBlank() || tripId.contains("/")) {
            return null;
        }
        try {
            return UUID.fromString(tripId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID resolveOutboundUserId(StompHeaderAccessor accessor) {
        UUID directUserId = parseUserId(accessor.getUser());
        if (directUserId != null) {
            return directUserId;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        SimpUserRegistry simpUserRegistry = simpUserRegistryProvider.getIfAvailable();
        if (simpUserRegistry == null) {
            return null;
        }
        return simpUserRegistry.getUsers().stream()
                .filter(user -> hasSession(user, sessionId))
                .map(SimpUser::getName)
                .map(this::parseUserId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean hasSession(SimpUser user, String sessionId) {
        return user.getSessions().stream()
                .map(SimpSession::getId)
                .anyMatch(sessionId::equals);
    }

    private UUID parseUserId(java.security.Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID parseUserId(String name) {
        if (name == null) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
