package com.ds.goroute.service;

import com.ds.goroute.config.AiTripWorkerProperties;
import com.ds.goroute.config.InternalApiProperties;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.mapper.AiTripGenerationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class AiTripWorkerDispatcher {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ObjectMapper objectMapper;
    private final AiTripGenerationMapper mapper;
    private final AiTripGenerationService generationService;
    private final RestTemplate restTemplate;
    private final AiTripWorkerProperties workerProperties;
    private final InternalApiProperties internalApiProperties;

    public AiTripWorkerDispatcher(
            ObjectMapper objectMapper,
            AiTripGenerationMapper mapper,
            AiTripGenerationService generationService,
            @Qualifier("aiTripWorkerRestTemplate") RestTemplate restTemplate,
            AiTripWorkerProperties workerProperties,
            InternalApiProperties internalApiProperties) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.generationService = generationService;
        this.restTemplate = restTemplate;
        this.workerProperties = workerProperties;
        this.internalApiProperties = internalApiProperties;
    }

    @Async
    public void dispatch(AiTripGenerationJob job) {
        if (mapper.claimForDispatch(job.getId(), job.getAttemptId()) != 1) {
            return;
        }
        try {
            String internalToken = internalApiProperties.aiTripToken();
            if (internalToken == null || internalToken.isBlank()) {
                throw new IllegalStateException("AI trip internal authentication is not configured");
            }

            Map<String, Object> payload = Map.of(
                    "jobId", job.getId().toString(),
                    "attemptId", job.getAttemptId(),
                    "request", objectMapper.readTree(job.getRequestPayload()),
                    "locale", job.getLocale(),
                    "callbackBaseUrl", workerProperties.getInternalBaseUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(INTERNAL_TOKEN_HEADER, internalToken);

            String url = stripTrailingSlash(workerProperties.getWorker().getBaseUrl()) + "/v1/ai-trip/jobs";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<>() {});

            log.info("Dispatched AI trip job {} with response status {}", job.getId(), response.getStatusCode());
        } catch (Exception error) {
            log.error("Cannot dispatch AI trip job {}", job.getId(), error);
            generationService.fail(job.getId(), "Could not start AI worker");
        }
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
