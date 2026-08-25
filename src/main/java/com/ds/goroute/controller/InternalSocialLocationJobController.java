package com.ds.goroute.controller;

import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.service.SocialLocationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/internal/social-location/jobs")
@RequiredArgsConstructor
@Tag(name = "Internal Social Location Jobs", description = "Python social-location callback APIs")
public class InternalSocialLocationJobController {

    private final SocialLocationJobService socialLocationJobService;

    @PostMapping("/callback")
    @Operation(summary = "Receive completed social-location extraction result from Python")
    public ResponseEntity<SocialLocationJobResponse> callback(
            @Valid @RequestBody SocialLocationJobCallbackRequest request) {
        return ResponseEntity.ok(socialLocationJobService.handleCallback(request));
    }
}
