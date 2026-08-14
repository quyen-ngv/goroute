package com.ds.goroute.controller;

import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.service.SocialLocationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/internal/social-location/jobs")
@RequiredArgsConstructor
@Tag(name = "Internal Social Location Jobs", description = "Python social-location callback APIs")
public class InternalSocialLocationJobController {

    private final SocialLocationJobService socialLocationJobService;

    @Value("${scrape.service.callback-token:}")
    private String callbackToken;

    @PostMapping("/callback")
    @Operation(summary = "Receive completed social-location extraction result from Python")
    public ResponseEntity<SocialLocationJobResponse> callback(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SocialLocationJobCallbackRequest request) {
        if (callbackToken != null && !callbackToken.isBlank() && !callbackToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal callback token");
        }
        return ResponseEntity.ok(socialLocationJobService.handleCallback(request));
    }
}
