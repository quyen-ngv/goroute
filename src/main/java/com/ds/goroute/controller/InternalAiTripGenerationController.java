package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripCandidateQueryRequest;
import com.ds.goroute.dto.request.AiTripCommitRequest;
import com.ds.goroute.dto.request.AiTripJobEventRequest;
import com.ds.goroute.service.AiTripGenerationService;
import com.ds.goroute.service.BaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/internal/ai-trip-generations")
@RequiredArgsConstructor
public class InternalAiTripGenerationController extends BaseService {

    private final AiTripGenerationService service;

    @PostMapping("/{jobId}/events")
    public ResponseEntity<BaseResponse<Void>> event(
            @PathVariable UUID jobId,
            @Valid @RequestBody AiTripJobEventRequest request) {
        service.acceptEvent(jobId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{jobId}/candidates")
    public ResponseEntity<BaseResponse<List<Map<String, Object>>>> candidates(
            @PathVariable UUID jobId,
            @RequestHeader("X-Attempt-Id") String attemptId,
            @Valid @RequestBody AiTripCandidateQueryRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.candidates(jobId, attemptId, request)));
    }

    @PostMapping("/{jobId}/commit")
    public ResponseEntity<BaseResponse<Map<String, UUID>>> commit(
            @PathVariable UUID jobId,
            @Valid @RequestBody AiTripCommitRequest request) {
        return ResponseEntity.ok(ofSucceeded(Map.of("tripId", service.commit(jobId, request))));
    }
}
