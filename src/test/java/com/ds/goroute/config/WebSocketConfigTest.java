package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.realtime.RealtimeSessionRegistry;
import com.ds.goroute.service.realtime.TripRealtimeAccessCache;
import com.ds.goroute.type.UserRealtimeEventType;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private MarketplaceConversationAccessService marketplaceAccess;
    @Mock
    private TripAccessGuard tripAccessGuard;
    @Mock
    private TripRealtimeAccessCache tripRealtimeAccessCache;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ObjectProvider<UserRealtimePublisher> userRealtimePublisherProvider;
    @Mock
    private UserRealtimePublisher userRealtimePublisher;
    @Mock
    private ChannelRegistration registration;
    @Mock
    private MessageChannel channel;

    private final RealtimeSessionRegistry sessionRegistry = new RealtimeSessionRegistry();

    @BeforeEach
    void publisherIsAvailable() {
        lenient().when(userRealtimePublisherProvider.getIfAvailable()).thenReturn(userRealtimePublisher);
    }

    // CONNECT

    @Test
    void connectAuthenticatesAndRemembersWhoOwnsTheSession() {
        UUID userId = UUID.randomUUID();
        givenValidToken("valid-token", userId);

        Message<?> connected = inboundInterceptor().preSend(connect("valid-token", "session-1"), channel);

        assertThat(StompHeaderAccessor.wrap(connected).getUser().getName()).isEqualTo(userId.toString());
        assertThat(sessionRegistry.userIdOf("session-1")).isEqualTo(userId);
    }

    @Test
    void rejectsUnauthenticatedConnect() {
        assertThatThrownBy(() -> inboundInterceptor().preSend(connect(null, "session-1"), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(sessionRegistry.userIdOf("session-1")).isNull();
    }

    @Test
    void disconnectForgetsTheSession() {
        sessionRegistry.register("session-1", UUID.randomUUID());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-1");

        inboundInterceptor().preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);

        assertThat(sessionRegistry.userIdOf("session-1")).isNull();
    }

    // SEND

    @Test
    void aClientCannotSendStraightToABrokerTopic() {
        UUID tripId = UUID.randomUUID();

        assertThat(inboundInterceptor().preSend(send("/topic/trips/" + tripId), channel)).isNull();
        assertThat(inboundInterceptor().preSend(send("/topic/users/" + UUID.randomUUID() + "/events"), channel))
                .isNull();
    }

    @Test
    void aClientCanSendToAnApplicationHandler() {
        Message<?> typing = send("/app/conversations/" + UUID.randomUUID() + "/typing");

        assertThat(inboundInterceptor().preSend(typing, channel)).isSameAs(typing);
    }

    // SUBSCRIBE

    @Test
    void aTripMemberCanSubscribeToTheirTripTopic() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Message<?> subscribe = subscribe("/topic/trips/" + tripId, userId);

        assertThat(inboundInterceptor().preSend(subscribe, channel)).isSameAs(subscribe);
        verify(tripAccessGuard).requireAccess(tripId, userId);
        verifyNoInteractions(userRealtimePublisher);
    }

    @Test
    void aDeniedSubscriptionIsDroppedAndTheSubscriberIsTold() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        doThrow(new AccessDeniedException("not a trip member"))
                .when(tripAccessGuard).requireAccess(tripId, userId);

        Message<?> result = inboundInterceptor().preSend(subscribe("/topic/trips/" + tripId, userId), channel);

        assertThat(result).isNull();
        verify(userRealtimePublisher).send(
                UserRealtimeEventType.SUBSCRIPTION_DENIED,
                userId,
                Map.of("destination", "/topic/trips/" + tripId, "subscriptionId", "sub-0"));
    }

    @Test
    void aTripDestinationWithASecondPathSegmentIsRefusedWithoutAsking() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        assertThat(inboundInterceptor().preSend(subscribe("/topic/trips/" + tripId + "/private", userId), channel))
                .isNull();
        verify(tripAccessGuard, never()).requireAccess(any(), any());
    }

    @Test
    void wildcardAndUnknownDestinationsAreRefused() {
        UUID userId = UUID.randomUUID();
        ChannelInterceptor interceptor = inboundInterceptor();

        for (String destination : new String[] {
                "/topic/**", "/topic/trips/*", "/topic/trips/**", "/topic/users/*/events",
                "/topic/users/{id}/chat", "/topic/marketplace/conversations/*", "/topic/other", "/queue/x"}) {
            assertThat(interceptor.preSend(subscribe(destination, userId), channel)).as(destination).isNull();
        }
        verify(tripAccessGuard, never()).requireAccess(any(), any());
        verify(marketplaceAccess, never()).requireAccess(any(), any());
    }

    @Test
    void anUnauthenticatedSubscriptionIsDroppedWithNobodyToTell() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trips/" + UUID.randomUUID());

        assertThat(inboundInterceptor().preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel)).isNull();
        verifyNoInteractions(tripAccessGuard, userRealtimePublisher);
    }

    @Test
    void somebodyCanFollowTheirOwnInboxAndEvents() {
        UUID userId = UUID.randomUUID();
        ChannelInterceptor interceptor = inboundInterceptor();

        assertThat(interceptor.preSend(subscribe("/topic/users/" + userId + "/chat", userId), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/users/" + userId + "/events", userId), channel)).isNotNull();
    }

    @Test
    void nobodyCanFollowSomebodyElsesInboxOrEvents() {
        UUID me = UUID.randomUUID();
        UUID somebodyElse = UUID.randomUUID();
        ChannelInterceptor interceptor = inboundInterceptor();

        assertThat(interceptor.preSend(subscribe("/topic/users/" + somebodyElse + "/chat", me), channel)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/users/" + somebodyElse + "/events", me), channel)).isNull();
    }

    @Test
    void aConversationSubscriptionIsCheckedAgainstTheConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        assertThat(inboundInterceptor().preSend(
                subscribe("/topic/marketplace/conversations/" + conversationId, userId), channel)).isNotNull();
        verify(marketplaceAccess).requireAccess(conversationId, userId);
    }

    // Outbound

    @Test
    void aTripMessageIsCheckedForTheSessionsUserThroughTheCache() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        sessionRegistry.register("session-1", userId);
        when(tripRealtimeAccessCache.canRead(tripId, userId)).thenReturn(true);

        assertThat(outboundInterceptor().preSend(brokerMessage("/topic/trips/" + tripId, "session-1"), channel))
                .isNotNull();
        verifyNoInteractions(tripAccessGuard);
    }

    @Test
    void aTripMessageIsDroppedForAMemberRemovedAfterSubscribing() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        sessionRegistry.register("session-1", userId);
        when(tripRealtimeAccessCache.canRead(tripId, userId)).thenReturn(false);

        assertThat(outboundInterceptor().preSend(brokerMessage("/topic/trips/" + tripId, "session-1"), channel))
                .isNull();
    }

    @Test
    void aTripMessageIsDroppedWhenTheCheckItselfFails() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        sessionRegistry.register("session-1", userId);
        when(tripRealtimeAccessCache.canRead(tripId, userId)).thenThrow(new IllegalStateException("db down"));

        assertThat(outboundInterceptor().preSend(brokerMessage("/topic/trips/" + tripId, "session-1"), channel))
                .isNull();
    }

    @Test
    void aMessageForAnUnknownSessionFailsClosed() {
        assertThat(outboundInterceptor().preSend(
                brokerMessage("/topic/trips/" + UUID.randomUUID(), "gone-session"), channel)).isNull();
        assertThat(outboundInterceptor().preSend(
                brokerMessage("/topic/marketplace/conversations/" + UUID.randomUUID(), "gone-session"), channel))
                .isNull();
        verifyNoInteractions(tripRealtimeAccessCache, marketplaceAccess);
    }

    @Test
    void userTopicsAreDeliveredOnlyToTheirOwnersSession() {
        UUID owner = UUID.randomUUID();
        UUID eavesdropper = UUID.randomUUID();
        sessionRegistry.register("owner-session", owner);
        sessionRegistry.register("other-session", eavesdropper);
        ChannelInterceptor interceptor = outboundInterceptor();

        for (String suffix : new String[] {"/chat", "/events"}) {
            String destination = "/topic/users/" + owner + suffix;
            assertThat(interceptor.preSend(brokerMessage(destination, "owner-session"), channel)).isNotNull();
            assertThat(interceptor.preSend(brokerMessage(destination, "other-session"), channel)).isNull();
        }
    }

    @Test
    void aConversationMessageReachesAMemberWhoStillHasAccess() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        sessionRegistry.register("session-1", userId);

        assertThat(outboundInterceptor().preSend(
                brokerMessage("/topic/marketplace/conversations/" + conversationId, "session-1"), channel))
                .isNotNull();
        verify(marketplaceAccess).requireAccess(conversationId, userId);
    }

    /**
     * The privacy rule that the subscribe-time check cannot keep on its own: somebody removed
     * from a trip, or blocked, holds an open subscription until they close the app.
     */
    @Test
    void aConversationMessageIsDroppedForSomebodyRemovedAfterSubscribing() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        sessionRegistry.register("session-1", userId);
        doThrow(new AccessDeniedException("no longer a member"))
                .when(marketplaceAccess).requireAccess(conversationId, userId);

        assertThat(outboundInterceptor().preSend(
                brokerMessage("/topic/marketplace/conversations/" + conversationId, "session-1"), channel))
                .isNull();
    }

    private void givenValidToken(String token, UUID userId) {
        Claims claims = mock(Claims.class);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getClaimsFromToken(token)).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
    }

    private WebSocketConfig config() {
        return new WebSocketConfig(marketplaceAccess, tripAccessGuard, tripRealtimeAccessCache, sessionRegistry,
                jwtUtils, userRealtimePublisherProvider, mock(TaskScheduler.class));
    }

    private ChannelInterceptor inboundInterceptor() {
        ChannelRegistration channelRegistration = mock(ChannelRegistration.class);
        config().configureClientInboundChannel(channelRegistration);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(channelRegistration).interceptors(captor.capture());
        return captor.getValue();
    }

    private ChannelInterceptor outboundInterceptor() {
        ChannelRegistration channelRegistration = mock(ChannelRegistration.class);
        config().configureClientOutboundChannel(channelRegistration);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(channelRegistration).interceptors(captor.capture());
        return captor.getValue();
    }

    private Message<byte[]> connect(String token, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        if (token != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + token);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> send(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** What the simple broker hands the outbound channel: a session id, no principal. */
    private Message<byte[]> brokerMessage(String destination, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination(destination);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
