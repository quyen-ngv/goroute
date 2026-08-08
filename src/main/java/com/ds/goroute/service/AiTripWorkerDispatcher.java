package com.ds.goroute.service;

import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.mapper.AiTripGenerationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service @RequiredArgsConstructor @Slf4j
public class AiTripWorkerDispatcher {
    private final ObjectMapper objectMapper;
    private final AiTripGenerationMapper mapper;
    private final AiTripGenerationService generationService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${ai-trip.worker.base-url:http://localhost:8091}") private String workerBaseUrl;
    @Value("${ai-trip.internal-base-url:http://localhost:8080}") private String internalBaseUrl;
    @Value("${ai-trip.internal-token:}") private String internalToken;

    @Async
    public void dispatch(AiTripGenerationJob job) {
        if (mapper.claimForDispatch(job.getId(), job.getAttemptId()) != 1) return;
        try {
            var requestPayload = objectMapper.readTree(job.getRequestPayload());
            
            Map<String, Object> payload = Map.of(
                    "jobId", job.getId().toString(),
                    "attemptId", job.getAttemptId(),
                    "request", requestPayload,
                    "locale", job.getLocale(),
                    "callbackBaseUrl", internalBaseUrl
            );
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalToken);
            
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
            
            String url = workerBaseUrl + "/v1/ai-trip/jobs";
            
            log.info("=== DISPATCHING AI TRIP JOB ===");
            log.info("URL: {}", url);
            log.info("Headers: {}", headers);
            log.info("Payload: {}", payloadJson);
            log.info("Payload length: {} bytes", payloadJson.getBytes().length);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            log.info("Response status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("AI worker returned " + response.getStatusCode());
            }
        } catch (Exception error) {
            log.error("Cannot dispatch AI trip job {}", job.getId(), error);
            generationService.fail(job.getId(), "Could not start AI worker");
        }
    }
}
