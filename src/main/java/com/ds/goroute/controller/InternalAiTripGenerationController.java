package com.ds.goroute.controller;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripCandidateQueryRequest;
import com.ds.goroute.dto.request.AiTripCommitRequest;
import com.ds.goroute.dto.request.AiTripJobEventRequest;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.AiTripGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/internal/ai-trip-generations")
@RequiredArgsConstructor
public class InternalAiTripGenerationController extends BaseController {

    private final AiTripGenerationService service;

    @PostMapping("/{jobId}/events")
    public ResponseEntity<BaseResponse<Void>> event(
            @PathVariable UUID jobId,
            @Valid @RequestBody AiTripJobEventRequest request) {
        service.acceptEvent(jobId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{jobId}/candidates")
    public ResponseEntity<BaseResponse<List<Map<String, Object>>>> candidates(
            @PathVariable UUID jobId,
            @RequestHeader("X-Attempt-Id") String attemptId,
            @Valid @RequestBody AiTripCandidateQueryRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.candidates(jobId, attemptId, request)));
    }

    @GetMapping("/{jobId}/config")
    public ResponseEntity<BaseResponse<Map<String, String>>> config(
            @PathVariable UUID jobId,
            @RequestHeader("X-Attempt-Id") String attemptId) {
        return ResponseEntity.ok(ofSucceeded(service.promptConfig(jobId, attemptId)));
    }

    /**
     * The commit is refused when the user ran out of trip creation slots while the generation was
     * running -- rare, because {@code create} checks both allowances up front, but possible when a
     * slot is spent elsewhere in between.
     *
     * <p>Failing the job has to happen out here rather than inside {@code commit}: that method is
     * transactional and the refusal rolls it back, so a status written alongside it would be rolled
     * back too. Without this, the job would sit RUNNING until the 20-minute watchdog swept it and
     * the user would be told it timed out, which is not what happened.
     */
    @PostMapping("/{jobId}/commit")
    public ResponseEntity<BaseResponse<Map<String, UUID>>> commit(
            @PathVariable UUID jobId,
            @Valid @RequestBody AiTripCommitRequest request) {
        try {
            return ResponseEntity.ok(ofSucceeded(Map.of("tripId", service.commit(jobId, request))));
        } catch (BusinessException error) {
            if (error.getError() != null
                    && error.getError().getCode() == ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED) {
                service.fail(jobId, "TRIP_CREATION_QUOTA_EXHAUSTED");
            }
            throw error;
        }
    }
}
