package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.SocialLocationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/social-location/jobs")
@RequiredArgsConstructor
@Tag(name = "Social Location Jobs", description = "Async TikTok/Instagram location extraction jobs")
public class SocialLocationJobController extends BaseService {

    private final SocialLocationJobService socialLocationJobService;

    @PostMapping
    @Operation(summary = "Create an async social-location extraction job")
    public ResponseEntity create(
            @Valid @RequestBody CreateSocialLocationJobRequest request,
            @RequestAttribute("userId") UUID userId) {
        SocialLocationJobResponse response = socialLocationJobService.create(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get a social-location extraction job")
    public ResponseEntity get(
            @PathVariable UUID jobId,
            @RequestAttribute("userId") UUID userId) {
        SocialLocationJobResponse response = socialLocationJobService.get(userId, jobId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/me")
    @Operation(summary = "List current user's social-location extraction jobs")
    public ResponseEntity listMine(
            @RequestAttribute("userId") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SocialLocationJobResponse> response = socialLocationJobService.listMine(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
