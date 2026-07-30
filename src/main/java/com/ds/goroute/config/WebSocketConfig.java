package com.ds.goroute.config;

import com.ds.goroute.service.MarketplaceConversationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String MARKETPLACE_TOPIC = "/topic/marketplace/conversations/";
    private final MarketplaceConversationAccessService marketplaceConversationAccessService;

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
                return message;
            }
        });
    }
}
