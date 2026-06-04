package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripConfirmRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.response.AiTripConfirmResponse;
import com.ds.goroute.dto.response.AiTripGenerateResponse;
import com.ds.goroute.service.AiTripService;
import com.ds.goroute.service.BaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/ai-trips")
@RequiredArgsConstructor
public class AiTripController extends BaseService {

    private final AiTripService aiTripService;

    @PostMapping("/drafts/generate")
    public ResponseEntity<BaseResponse<AiTripGenerateResponse>> generateCandidates(
            @Valid @RequestBody AiTripGenerateRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(aiTripService.generateCandidates(request, userId)));
    }

    @PostMapping("/drafts/{draftId}/confirm")
    public ResponseEntity<BaseResponse<AiTripConfirmResponse>> confirmTrip(
            @PathVariable UUID draftId,
            @Valid @RequestBody AiTripConfirmRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(aiTripService.confirmTrip(draftId, request, userId)));
    }
}
