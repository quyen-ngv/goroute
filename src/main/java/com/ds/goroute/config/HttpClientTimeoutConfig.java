package com.ds.goroute.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Default connect/read timeouts for every auto-configured {@link
 * org.springframework.web.client.RestClient.Builder} in the application.
 *
 * <p>Spring's builder ships with no timeouts at all, so an upstream that accepted the
 * connection and then stopped answering held the calling thread until the socket died on
 * its own. That is how a stalled image-moderation host could block an upload for ever:
 * the caller only fails open when an exception is thrown, and a hang never throws one.
 *
 * <p>Clients that need a different budget still set their own request factory and are
 * unaffected — the AI clients allow 90 seconds for a completion, and Goong 15 seconds.
 * A {@code RestClientCustomizer} only reaches the auto-configured builder, so a builder
 * created by hand in a test (with {@code MockRestServiceServer} bound to it) keeps the
 * request factory the test installed.
 */
@Configuration
public class HttpClientTimeoutConfig {

    @Value("${goroute.http-client.default.connect-timeout-ms:5000}")
    private int connectTimeoutMillis;

    @Value("${goroute.http-client.default.read-timeout-ms:30000}")
    private int readTimeoutMillis;

    @Bean
    public RestClientCustomizer defaultTimeoutsRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
            builder.requestFactory(factory);
        };
    }
}
