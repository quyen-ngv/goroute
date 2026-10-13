package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.service.AiTripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** AI trip quota for the app. Generation itself runs through {@link AiTripGenerationController}. */
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
}
