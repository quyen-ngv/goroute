package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void rejectsUnauthenticatedConnectBeforeAnyTripSubscriptionIsAccepted() {
        assertThatThrownBy(() -> inboundInterceptor().preSend(connect(null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ChannelInterceptor inboundInterceptor() {
        WebSocketConfig config = new WebSocketConfig(marketplaceAccess, tripAccessGuard, jwtUtils);
        config.configureClientInboundChannel(registration);
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
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trips/" + tripId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
