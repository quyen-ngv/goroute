package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.response.AiTripJobResponse;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/ai-trip-generations")
@RequiredArgsConstructor
@Slf4j
public class AiTripGenerationController extends BaseController {
    private static final String STREAM_ERROR_MESSAGE = "Unable to start AI trip generation";

    private final AiTripGenerationService service;
    private final AiTripWorkerDispatcher dispatcher;

    @PostMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createAndStream(@Valid @RequestBody AiTripGenerateRequest request,
                                      @CurrentUser UUID userId,
                                      @RequestHeader(value="Idempotency-Key",required=false) String key,
                                      @RequestHeader(value="Accept-Language",required=false) String locale) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        log.info("AI trip stream requested: user={} city={} idempotencyKey={}", userId, request.getCityName(), key);
        try {
            AiTripGenerationJob job=service.create(request,userId,key,locale);
            SseEmitter actualEmitter=service.subscribe(job.getId(),userId,0);
            log.info("AI trip stream open: job={} status={} user={}", job.getId(), job.getStatus(), userId);
            if ("QUEUED".equals(job.getStatus())) dispatcher.dispatch(job);
            return actualEmitter;
        } catch (Exception exception) {
            // Send error as SSE event instead of throwing (which would try to return BaseResponse)
            log.error("Could not create AI trip generation stream for user {}", userId, exception);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", STREAM_ERROR_MESSAGE), MediaType.APPLICATION_JSON));
            } catch (Exception sendException) {
                log.error("Could not send AI trip generation stream error event", sendException);
            } finally {
                emitter.complete();
            }
            return emitter;
        }
    }

    @GetMapping("/active")
    public ResponseEntity<BaseResponse<AiTripJobResponse>> active(@CurrentUser UUID userId){return ResponseEntity.ok(ofSucceeded(service.active(userId)));}
    @GetMapping("/{jobId}")
    public ResponseEntity<BaseResponse<AiTripJobResponse>> get(@PathVariable UUID jobId,@CurrentUser UUID userId){return ResponseEntity.ok(ofSucceeded(service.get(jobId,userId)));}
    @GetMapping(value="/{jobId}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID jobId,@CurrentUser UUID userId,@RequestHeader(value="Last-Event-ID",defaultValue="0") long after){return service.subscribe(jobId,userId,after);}
    @DeleteMapping("/{jobId}")
    public ResponseEntity<BaseResponse<Void>> cancel(@PathVariable UUID jobId,@CurrentUser UUID userId){service.cancel(jobId,userId);return ResponseEntity.ok(ofSucceeded(null));}
}
