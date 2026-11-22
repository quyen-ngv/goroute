package com.ds.goroute.service.assistant;

import com.ds.goroute.config.AiTripWorkerProperties;
import com.ds.goroute.config.InternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Hands one turn to the Python worker (the same service that plans AI trips). The worker
 * accepts at once and answers through the internal callbacks; nothing waits on this call
 * beyond the hand-off.
 */
@Component
public class AssistantWorkerClient {

    static final String PATH = "/v1/assistant/turns";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final AiTripWorkerProperties workerProperties;
    private final InternalApiProperties internalApiProperties;

    public AssistantWorkerClient(@Qualifier("aiTripWorkerRestTemplate") RestTemplate restTemplate,
                                 AiTripWorkerProperties workerProperties,
                                 InternalApiProperties internalApiProperties) {
        this.restTemplate = restTemplate;
        this.workerProperties = workerProperties;
        this.internalApiProperties = internalApiProperties;
    }

    /** Throws when the worker is unreachable or refuses; the caller fails the turn. */
    public void start(Map<String, Object> payload) {
        String token = internalApiProperties.aiTripToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("AI worker internal authentication is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTERNAL_TOKEN_HEADER, token);
        String url = workerProperties.getWorker().getBaseUrl().replaceAll("/+$", "") + PATH;
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }
}
