package com.ds.goroute.controller;

import com.ds.goroute.dto.request.NationwideJobEventRequest;
import com.ds.goroute.dto.request.NationwideDuplicateCheckRequest;
import com.ds.goroute.dto.request.NationwidePlaceImportRequest;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.NationwidePlaceImportJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/internal/place-import-jobs/nationwide")
@RequiredArgsConstructor
public class InternalNationwidePlaceImportController extends BaseService {
    private final NationwidePlaceImportJobService service;

    @Value("${scrape.service.callback-token:}")
    private String callbackToken;

    @PostMapping("/events")
    public ResponseEntity<?> event(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                   @Valid @RequestBody NationwideJobEventRequest request) {
        verifyToken(token);
        service.acceptEvent(request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/imports")
    public ResponseEntity<?> importPlace(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                         @Valid @RequestBody NationwidePlaceImportRequest request) {
        verifyToken(token);
        return ResponseEntity.ok(ofSucceeded(service.importCandidate(request)));
    }

    @PostMapping("/existing-candidates")
    public ResponseEntity<?> existingCandidates(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody NationwideDuplicateCheckRequest request) {
        verifyToken(token);
        return ResponseEntity.ok(ofSucceeded(service.findExistingCandidates(request)));
    }

    private void verifyToken(String token) {
        if (callbackToken != null && !callbackToken.isBlank() && !callbackToken.equals(token)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }
    }
}
