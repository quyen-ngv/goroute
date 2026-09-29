package com.ds.goroute.config;

import com.ds.goroute.config.filter.ApiKeyAuthenticationFilter;
import com.ds.goroute.config.filter.CorsFilter;
import com.ds.goroute.config.filter.InternalApiAuthenticationFilter;
import com.ds.goroute.config.filter.JwtAuthenticationFilter;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.realtime.RealtimeSessionRegistry;
import com.ds.goroute.service.realtime.TripRealtimeAccessCache;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The socket as a phone reaches it: a raw WebSocket upgrade through the real security chain,
 * then a STOMP CONNECT carrying the bearer token. Boots only the web, security and broker slice.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WebSocketHandshakeTest.SocketSlice.class,
        properties = "application.security.jwt.secret-key=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=")
class WebSocketHandshakeTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            PropertyPlaceholderAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class})
    @Import({
            SecurityConfig.class,
            WebSocketConfig.class,
            JwtUtils.class,
            JwtAuthenticationFilter.class,
            ApiKeyAuthenticationFilter.class,
            InternalApiAuthenticationFilter.class,
            CorsFilter.class,
            ApiAuthenticationEntryPoint.class,
            ApiAccessDeniedHandler.class,
            RealtimeSessionRegistry.class,
            UserRealtimePublisher.class})
    static class SocketSlice {
    }

    @MockitoBean
    private AdminMapper adminMapper;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private MarketplaceConversationAccessService marketplaceConversationAccessService;
    @MockitoBean
    private TripAccessGuard tripAccessGuard;
    @MockitoBean
    private TripRealtimeAccessCache tripRealtimeAccessCache;

    @Autowired
    private JwtUtils jwtUtils;

    @LocalServerPort
    private int port;

    @Test
    void aRawUpgradeWithoutCredentialsIsAcceptedAndStompConnectCarriesTheToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateToken(Map.of("userId", userId.toString()), "someone@example.com");
        FrameCollector frames = new FrameCollector();

        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .subprotocols("v12.stomp")
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/v1/api/ws"), frames)
                .get(10, TimeUnit.SECONDS);
        socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:10000,10000\n"
                + "Authorization:Bearer " + token + "\n\n\u0000", true).get(5, TimeUnit.SECONDS);

        String connected = frames.next();
        assertThat(connected).startsWith("CONNECTED\n").contains("heart-beat:10000,10000");

        // A refused SUBSCRIBE leaves the socket open and is reported on the caller's own topic.
        socket.sendText("SUBSCRIBE\nid:events\ndestination:/topic/users/" + userId + "/events\n\n\u0000", true)
                .get(5, TimeUnit.SECONDS);
        socket.sendText("SUBSCRIBE\nid:sub-1\ndestination:/topic/**\n\n\u0000", true).get(5, TimeUnit.SECONDS);
        String denied = frames.next();
        assertThat(denied).startsWith("MESSAGE\n")
                .contains("destination:/topic/users/" + userId + "/events")
                .contains("\"type\":\"subscription.denied\"")
                .contains("\"subscriptionId\":\"sub-1\"");
        assertThat(socket.isInputClosed()).isFalse();
        socket.abort();
    }

    @Test
    void theEndpointNoLongerAnswersAsSockJs() throws Exception {
        HttpResponse<String> info = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/api/ws/info")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(info.statusCode()).isNotEqualTo(200);
    }

    /** Collects whole STOMP frames, skipping heart-beat newlines. */
    private static final class FrameCollector implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String frame = partial.toString();
                partial.setLength(0);
                if (!frame.isBlank()) {
                    frames.add(frame);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        String next() throws InterruptedException {
            String frame = frames.poll(10, TimeUnit.SECONDS);
            assertThat(frame).as("a STOMP frame within 10 s").isNotNull();
            return frame;
        }
    }
}
