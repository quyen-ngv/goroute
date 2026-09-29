package com.ds.goroute.config;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.realtime.RealtimeSessionRegistry;
import com.ds.goroute.service.realtime.TripRealtimeAccessCache;
import com.ds.goroute.type.UserRealtimeEventType;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * STOMP over a plain WebSocket at {@value #ENDPOINT}.
 *
 * <p>The HTTP upgrade is public; identity is the {@code Authorization: Bearer} header of the STOMP
 * CONNECT frame. After that, a client may SEND only to application handlers under {@code /app/}
 * and SUBSCRIBE only to the topics listed below, each behind its own access check. A refused
 * SUBSCRIBE or SEND is dropped rather than thrown: a thrown interceptor error becomes a STOMP
 * ERROR frame, which closes the whole socket, and a client that re-subscribes on reconnect would
 * then loop forever. The subscriber is told on its own events topic instead.
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    static final String ENDPOINT = "/v1/api/ws";
    private static final String APPLICATION_PREFIX = "/app";
    private static final String TOPIC_PREFIX = "/topic";
    private static final String MARKETPLACE_TOPIC = "/topic/marketplace/conversations/";
    private static final String TRIP_TOPIC = "/topic/trips/";
    private static final String USER_TOPIC_PREFIX = "/topic/users/";
    private static final List<String> USER_TOPIC_SUFFIXES = List.of("/chat", "/events");
    private static final String BEARER_PREFIX = "Bearer ";
    /** Server-to-client and expected client-to-server heart-beat, per the realtime contract. */
    static final long HEARTBEAT_MILLIS = 10_000;
    private static final int CANONICAL_UUID_LENGTH = 36;

    private final MarketplaceConversationAccessService marketplaceConversationAccessService;
    private final TripAccessGuard tripAccessGuard;
    private final TripRealtimeAccessCache tripRealtimeAccessCache;
    private final RealtimeSessionRegistry sessionRegistry;
    private final JwtUtils jwtUtils;
    /** Lazily resolved: the publisher needs the broker template, which is built from this configurer. */
    private final ObjectProvider<UserRealtimePublisher> userRealtimePublisherProvider;
    /**
     * The broker's own scheduler, which the message-broker configuration already declares. Reusing
     * it adds no second {@link TaskScheduler} bean, so {@code @Scheduled} resolution is unchanged.
     * Lazy for the same reason as the publisher.
     */
    private final TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(
            MarketplaceConversationAccessService marketplaceConversationAccessService,
            TripAccessGuard tripAccessGuard,
            TripRealtimeAccessCache tripRealtimeAccessCache,
            RealtimeSessionRegistry sessionRegistry,
            JwtUtils jwtUtils,
            ObjectProvider<UserRealtimePublisher> userRealtimePublisherProvider,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
        this.marketplaceConversationAccessService = marketplaceConversationAccessService;
        this.tripAccessGuard = tripAccessGuard;
        this.tripRealtimeAccessCache = tripRealtimeAccessCache;
        this.sessionRegistry = sessionRegistry;
        this.jwtUtils = jwtUtils;
        this.userRealtimePublisherProvider = userRealtimePublisherProvider;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Without a scheduler the simple broker answers CONNECT with heart-beat:0,0, and a
        // half-open mobile connection is never noticed by either side.
        config.enableSimpleBroker(TOPIC_PREFIX)
                .setTaskScheduler(messageBrokerTaskScheduler)
                .setHeartbeatValue(new long[] {HEARTBEAT_MILLIS, HEARTBEAT_MILLIS});
        config.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Plain WebSocket only. Every client (the app) opens a raw socket here; a SockJS endpoint
        // answers that upgrade with 400 because it only serves its own sub-paths.
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns("*");
        // Handle each session's frames in the order they arrived. Otherwise the inbound pool can
        // run a client's UNSUBSCRIBE before its SUBSCRIBE, or refuse a topic before the client's
        // own events subscription (where the refusal is reported) is registered.
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();
                if (command == null) {
                    return message;
                }
                return switch (command) {
                    case CONNECT, STOMP -> authenticate(message, accessor);
                    case DISCONNECT -> {
                        sessionRegistry.remove(accessor.getSessionId());
                        yield message;
                    }
                    case SEND -> authorizeSend(message, accessor);
                    case SUBSCRIBE -> authorizeSubscription(message, accessor);
                    default -> message;
                };
            }
        });
    }

    /**
     * The simple broker fans a topic message out to every session that subscribed to it.
     * Checking only SUBSCRIBE is not enough: a member can be removed or demoted while a
     * connection is still open. Re-check the current grant on the per-session outbound
     * message and fail closed when the session's user cannot be resolved.
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
                return canDeliver(accessor) ? message : null;
            }
        });
    }

    private Message<?> authorizeSend(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(APPLICATION_PREFIX + "/")) {
            return message;
        }
        // A SEND straight to a broker topic would be broadcast verbatim to its subscribers,
        // letting any client forge a server event.
        log.debug("Dropping a client SEND to {}", destination);
        return null;
    }

    private Message<?> authorizeSubscription(Message<?> message, StompHeaderAccessor accessor) {
        UUID userId = parseUserId(accessor.getUser());
        String destination = accessor.getDestination();
        if (userId != null && canSubscribe(destination, userId)) {
            return message;
        }
        denySubscription(userId, destination, accessor.getSubscriptionId());
        return null;
    }

    /**
     * Exact destinations only. Anything else, including broker patterns such as
     * {@code /topic/**} or {@code /topic/trips/*}, fails to parse and is refused.
     */
    private boolean canSubscribe(String destination, UUID userId) {
        UUID ownerId = parseUserDestination(destination);
        if (ownerId != null) {
            return ownerId.equals(userId);
        }
        UUID tripId = parseIdUnder(destination, TRIP_TOPIC);
        if (tripId != null) {
            return isGranted(() -> tripAccessGuard.requireAccess(tripId, userId));
        }
        UUID conversationId = parseIdUnder(destination, MARKETPLACE_TOPIC);
        if (conversationId != null) {
            return isGranted(() -> marketplaceConversationAccessService.requireAccess(conversationId, userId));
        }
        return false;
    }

    private void denySubscription(UUID userId, String destination, String subscriptionId) {
        if (userId == null) {
            // Unreachable for a session that passed CONNECT; nobody to tell.
            log.debug("Dropping an unauthenticated SUBSCRIBE to {}", destination);
            return;
        }
        log.debug("Refusing SUBSCRIBE to {} for user {}", destination, userId);
        UserRealtimePublisher publisher = userRealtimePublisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (destination != null) {
            payload.put("destination", destination);
        }
        if (subscriptionId != null) {
            payload.put("subscriptionId", subscriptionId);
        }
        publisher.send(UserRealtimeEventType.SUBSCRIPTION_DENIED, userId, payload);
    }

    private boolean canDeliver(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_PREFIX + "/")) {
            return true;
        }
        UUID ownerId = parseUserDestination(destination);
        if (ownerId != null) {
            return ownerId.equals(resolveOutboundUserId(accessor));
        }
        UUID tripId = parseIdUnder(destination, TRIP_TOPIC);
        if (tripId != null) {
            UUID subscriberId = resolveOutboundUserId(accessor);
            // Do not leak a trip event when membership changed after SUBSCRIBE.
            return subscriberId != null
                    && answersYes(() -> tripRealtimeAccessCache.canRead(tripId, subscriberId));
        }
        UUID conversationId = parseIdUnder(destination, MARKETPLACE_TOPIC);
        if (conversationId != null) {
            UUID subscriberId = resolveOutboundUserId(accessor);
            // Membership changed after SUBSCRIBE; the thread is no longer theirs.
            return subscriberId != null
                    && isGranted(() -> marketplaceConversationAccessService.requireAccess(conversationId, subscriberId));
        }
        // No other topic can be subscribed to, so nothing legitimate is lost here.
        return false;
    }

    /** Runs a check that answers by returning normally or throwing; a throw is a no. */
    private boolean isGranted(Runnable requirement) {
        return answersYes(() -> {
            requirement.run();
            return true;
        });
    }

    /** Fail-closed: a refusal or any failure while checking is a no. */
    private boolean answersYes(BooleanSupplier check) {
        try {
            return check.getAsBoolean();
        } catch (BusinessException | AccessDeniedException refused) {
            return false;
        } catch (RuntimeException failure) {
            log.warn("Socket access check failed, refusing: {}", failure.getMessage());
            return false;
        }
    }

    private Message<?> authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Authentication required for WebSocket connection");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
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
        Principal user = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        sessionRegistry.register(accessor.getSessionId(), userId);
        // The user must be set on the frame's own accessor, not on a wrap() copy. Spring's STOMP
        // handler remembers a principal for the rest of the session only through that accessor's
        // user-change callback; set on a copy, the broker saw the user on CONNECT but every later
        // SUBSCRIBE and SEND on the socket arrived anonymous.
        StompHeaderAccessor original = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (original != null && original.isMutable()) {
            original.setUser(user);
            return message;
        }
        accessor.setUser(user);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    /**
     * The broker copies no principal onto the per-session copy of a topic message, so the
     * subscriber is normally found through the session that CONNECT registered.
     */
    private UUID resolveOutboundUserId(StompHeaderAccessor accessor) {
        UUID directUserId = parseUserId(accessor.getUser());
        if (directUserId != null) {
            return directUserId;
        }
        return sessionRegistry.userIdOf(accessor.getSessionId());
    }

    /** The owner named by {@code /topic/users/<id>/chat} or {@code .../events}, else null. */
    private UUID parseUserDestination(String destination) {
        if (destination == null || !destination.startsWith(USER_TOPIC_PREFIX)) {
            return null;
        }
        for (String suffix : USER_TOPIC_SUFFIXES) {
            if (destination.endsWith(suffix)) {
                return parseCanonicalUuid(destination.substring(
                        USER_TOPIC_PREFIX.length(), destination.length() - suffix.length()));
            }
        }
        return null;
    }

    /** The id in {@code <prefix><id>} with nothing after it, else null. */
    private UUID parseIdUnder(String destination, String prefix) {
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }
        return parseCanonicalUuid(destination.substring(prefix.length()));
    }

    /**
     * Only the 36-character form the server itself publishes to. This also refuses anything
     * carrying a path separator or a broker wildcard.
     */
    private UUID parseCanonicalUuid(String value) {
        if (value == null || value.length() != CANONICAL_UUID_LENGTH) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID parseUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        return parseCanonicalUuid(principal.getName());
    }
}
