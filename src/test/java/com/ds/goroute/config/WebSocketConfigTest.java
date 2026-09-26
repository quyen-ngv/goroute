package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private MarketplaceConversationAccessService marketplaceAccess;
    @Mock
    private TripAccessGuard tripAccessGuard;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ObjectProvider<org.springframework.messaging.simp.user.SimpUserRegistry> simpUserRegistryProvider;
    @Mock
    private ChannelRegistration registration;
    @Mock
    private MessageChannel channel;

    @Test
    void authenticatedTripMemberCanSubscribeToOnlyTheirTripTopic() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwtUtils.validateToken("valid-token")).thenReturn(true);
        when(jwtUtils.getClaimsFromToken("valid-token")).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());

        ChannelInterceptor interceptor = inboundInterceptor();
        Message<?> connected = interceptor.preSend(connect("valid-token"), channel);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);
        assertThat(connectedAccessor.getUser().getName()).isEqualTo(userId.toString());

        interceptor.preSend(subscribe(tripId, userId), channel);

        verify(tripAccessGuard).requireAccess(tripId, userId);
    }

    @Test
    void rejectsSubscriptionWhenTripAccessGuardRefusesTheUser() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        doThrow(new AccessDeniedException("not a trip member"))
                .when(tripAccessGuard)
                .requireAccess(tripId, userId);

        assertThatThrownBy(() -> inboundInterceptor().preSend(subscribe(tripId, userId), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsTripDestinationWithASecondPathSegment() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        assertThatThrownBy(() -> inboundInterceptor().preSend(
                subscribeToDestination("/topic/trips/" + tripId + "/private"), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(tripAccessGuard, never()).requireAccess(tripId, userId);
    }

    @Test
    void rejectsUnauthenticatedConnectBeforeAnyTripSubscriptionIsAccepted() {
        assertThatThrownBy(() -> inboundInterceptor().preSend(connect(null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsTripMessageForMemberRemovedAfterSubscription() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        doThrow(new AccessDeniedException("membership revoked"))
                .when(tripAccessGuard).requireAccess(tripId, userId);

        ChannelInterceptor interceptor = outboundInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/trips/" + tripId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isNull();
    }

    @Test
    void deliversAConversationMessageToAMemberWhoStillHasAccess() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        assertThat(outboundInterceptor().preSend(conversationMessage(conversationId, userId), channel))
                .isNotNull();
        verify(marketplaceAccess).requireAccess(conversationId, userId);
    }

    /**
     * The privacy rule that the subscribe-time check cannot keep on its own: somebody removed
     * from a trip, or blocked, holds an open subscription until they close the app.
     */
    @Test
    void rejectsAConversationMessageForSomebodyRemovedAfterSubscription() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        doThrow(new AccessDeniedException("no longer a member"))
                .when(marketplaceAccess).requireAccess(conversationId, userId);

        assertThat(outboundInterceptor().preSend(conversationMessage(conversationId, userId), channel)).isNull();
    }

    /** No resolvable subscriber means no way to check, which fails closed. */
    @Test
    void rejectsAConversationMessageWithNoIdentifiableSubscriber() {
        ChannelInterceptor interceptor = outboundInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/marketplace/conversations/" + UUID.randomUUID());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isNull();
    }

    @Test
    void letsSomebodyFollowTheirOwnInbox() {
        UUID userId = UUID.randomUUID();

        inboundInterceptor().preSend(
                subscribeToDestinationAs("/topic/users/" + userId + "/chat", userId), channel);
    }

    @Test
    void refusesToLetAnybodyFollowSomebodyElseInbox() {
        UUID me = UUID.randomUUID();
        UUID somebodyElse = UUID.randomUUID();

        assertThatThrownBy(() -> inboundInterceptor().preSend(
                subscribeToDestinationAs("/topic/users/" + somebodyElse + "/chat", me), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deliversAnInboxEventToItsOwner() {
        UUID userId = UUID.randomUUID();

        assertThat(outboundInterceptor().preSend(inboxMessage(userId, userId), channel)).isNotNull();
    }

    @Test
    void neverDeliversAnInboxEventToAnybodyElse() {
        UUID owner = UUID.randomUUID();
        UUID eavesdropper = UUID.randomUUID();

        assertThat(outboundInterceptor().preSend(inboxMessage(owner, eavesdropper), channel)).isNull();
    }

    private Message<?> inboxMessage(UUID owner, UUID subscriber) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/users/" + owner + "/chat");
        accessor.setUser(new UsernamePasswordAuthenticationToken(subscriber.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> subscribeToDestinationAs(String destination, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> conversationMessage(UUID conversationId, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/marketplace/conversations/" + conversationId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ChannelInterceptor inboundInterceptor() {
        WebSocketConfig config = new WebSocketConfig(
                marketplaceAccess, tripAccessGuard, jwtUtils, simpUserRegistryProvider);
        config.configureClientInboundChannel(registration);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(registration).interceptors(captor.capture());
        return captor.getValue();
    }

    private ChannelInterceptor outboundInterceptor() {
        WebSocketConfig config = new WebSocketConfig(
                marketplaceAccess, tripAccessGuard, jwtUtils, simpUserRegistryProvider);
        config.configureClientOutboundChannel(registration);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(registration).interceptors(captor.capture());
        return captor.getValue();
    }

    private Message<byte[]> connect(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (token != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + token);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(UUID tripId, UUID userId) {
        return subscribeToDestination("/topic/trips/" + tripId, userId);
    }

    private Message<byte[]> subscribeToDestination(String destination) {
        return subscribeToDestination(destination, UUID.randomUUID());
    }

    private Message<byte[]> subscribeToDestination(String destination, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
