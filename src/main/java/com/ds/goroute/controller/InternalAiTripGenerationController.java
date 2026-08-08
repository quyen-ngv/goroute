package com.ds.goroute.controller;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/api/internal/ai-trip-generations")
@RequiredArgsConstructor
public class InternalAiTripGenerationController extends BaseService {
    private final AiTripGenerationService service;
    @Value("${ai-trip.internal-token:}") private String internalToken;

    @PostMapping("/{jobId}/events")
    public ResponseEntity<?> event(@PathVariable UUID jobId,@RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody AiTripJobEventRequest request){verify(token);service.acceptEvent(jobId,request);return ResponseEntity.ok(ofSucceeded(null));}
    @PostMapping("/{jobId}/candidates")
    public ResponseEntity<?> candidates(@PathVariable UUID jobId,@RequestHeader(value="X-Internal-Token",required=false) String token,@RequestHeader("X-Attempt-Id") String attempt,@Valid @RequestBody AiTripCandidateQueryRequest request){verify(token);return ResponseEntity.ok(ofSucceeded(service.candidates(jobId,attempt,request)));}
    @PostMapping("/{jobId}/commit")
    public ResponseEntity<?> commit(@PathVariable UUID jobId,@RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody AiTripCommitRequest request){verify(token);return ResponseEntity.ok(ofSucceeded(Map.of("tripId",service.commit(jobId,request))));}
    private void verify(String token){if(internalToken!=null&&!internalToken.isBlank()&&!internalToken.equals(token))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid internal token");}
}
