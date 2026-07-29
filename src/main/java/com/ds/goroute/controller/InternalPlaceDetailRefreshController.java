package com.ds.goroute.controller;

import com.ds.goroute.dto.request.PlaceDetailRefreshJobEventRequest;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceDetailRefreshJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/api/internal/place-import-jobs/place-details-refresh")
@RequiredArgsConstructor
public class InternalPlaceDetailRefreshController extends BaseService {
    private final PlaceDetailRefreshJobService service;

    @Value("${scrape.service.callback-token:}")
    private String callbackToken;

    @PostMapping("/events")
    public ResponseEntity<?> event(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody PlaceDetailRefreshJobEventRequest request) {
        verifyToken(token);
        service.acceptEvent(request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    private void verifyToken(String token) {
        if (callbackToken != null && !callbackToken.isBlank() && !callbackToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }
    }
}
