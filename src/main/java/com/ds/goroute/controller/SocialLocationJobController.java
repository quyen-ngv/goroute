package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.service.SocialLocationJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/social-location/jobs")
@RequiredArgsConstructor
public class SocialLocationJobController extends BaseController {

    private final SocialLocationJobService socialLocationJobService;

    @PostMapping
    public ResponseEntity create(
            @Valid @RequestBody CreateSocialLocationJobRequest request,
            @CurrentUser UUID userId) {
        SocialLocationJobResponse response = socialLocationJobService.create(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity get(
            @PathVariable UUID jobId,
            @CurrentUser UUID userId) {
        SocialLocationJobResponse response = socialLocationJobService.get(userId, jobId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/me")
    public ResponseEntity listMine(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SocialLocationJobResponse> response = socialLocationJobService.listMine(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity delete(
            @PathVariable UUID jobId,
            @CurrentUser UUID userId) {
        socialLocationJobService.delete(userId, jobId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
