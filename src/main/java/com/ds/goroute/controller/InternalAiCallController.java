package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiApiCallLogRequest;
import com.ds.goroute.dto.response.AiCreditStatus;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.AiCallLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AI call logs reported by workers outside the AI trip job (scrape-callback token). The AI trip
 * worker reports under {@code /internal/ai-trip-generations/{jobId}/ai-calls} instead, so the
 * job's owner is attached.
 */
@RestController
@RequestMapping("/v1/api/internal/ai-calls")
@RequiredArgsConstructor
public class InternalAiCallController extends BaseController {
    private final AiCallLogService service;
    private final SocialLocationJobMapper socialJobMapper;

    @PostMapping
    public ResponseEntity<BaseResponse<AiCreditStatus>> record(@Valid @RequestBody AiApiCallLogRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.record(request, ownerOf(request))));
    }

    /** The social worker reports with traceId = backend job id; attach that job's user. */
    private UUID ownerOf(AiApiCallLogRequest request) {
        if (!"SOCIAL_LOCATION".equals(request.getFeature()) || request.getTraceId() == null) return null;
        try {
            SocialLocationJob job = socialJobMapper.findById(UUID.fromString(request.getTraceId()));
            return job == null ? null : job.getUserId();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
