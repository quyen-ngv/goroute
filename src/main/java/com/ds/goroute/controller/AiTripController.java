package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripConfirmRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.response.AiTripConfirmResponse;
import com.ds.goroute.dto.response.AiTripGenerateResponse;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.service.AiTripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/ai-trips")
@RequiredArgsConstructor
public class AiTripController extends BaseController {

    private final AiTripService aiTripService;

    @GetMapping("/eligibility")
    public ResponseEntity<BaseResponse<AiTripUsage>> getEligibility(
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(aiTripService.getEligibility(userId)));
    }

    @PostMapping("/drafts/generate")
    public ResponseEntity<BaseResponse<AiTripGenerateResponse>> generateCandidates(
            @Valid @RequestBody AiTripGenerateRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(aiTripService.generateCandidates(request, userId)));
    }

    @PostMapping("/drafts/{draftId}/confirm")
    public ResponseEntity<BaseResponse<AiTripConfirmResponse>> confirmTrip(
            @PathVariable UUID draftId,
            @Valid @RequestBody AiTripConfirmRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(aiTripService.confirmTrip(draftId, request, userId)));
    }
}
