package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.response.AiTripJobResponse;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/ai-trip-generations")
@RequiredArgsConstructor
public class AiTripGenerationController extends BaseService {
    private final AiTripGenerationService service;
    private final AiTripWorkerDispatcher dispatcher;

    @PostMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createAndStream(@Valid @RequestBody AiTripGenerateRequest request,
                                      @RequestAttribute("userId") UUID userId,
                                      @RequestHeader(value="Idempotency-Key",required=false) String key,
                                      @RequestHeader(value="Accept-Language",required=false) String locale) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        try {
            AiTripGenerationJob job=service.create(request,userId,key,locale);
            SseEmitter actualEmitter=service.subscribe(job.getId(),userId,0);
            if ("QUEUED".equals(job.getStatus())) dispatcher.dispatch(job);
            return actualEmitter;
        } catch (Exception e) {
            // Send error as SSE event instead of throwing (which would try to return BaseResponse)
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"message\":\"" + errorMessage.replace("\"", "\\\"") + "\"}"));
                emitter.complete();
            } catch (Exception sendEx) {
                emitter.completeWithError(sendEx);
            }
            return emitter;
        }
    }

    @GetMapping("/active")
    public ResponseEntity<BaseResponse<AiTripJobResponse>> active(@RequestAttribute("userId") UUID userId){return ResponseEntity.ok(ofSucceeded(service.active(userId)));}
    @GetMapping("/{jobId}")
    public ResponseEntity<BaseResponse<AiTripJobResponse>> get(@PathVariable UUID jobId,@RequestAttribute("userId") UUID userId){return ResponseEntity.ok(ofSucceeded(service.get(jobId,userId)));}
    @GetMapping(value="/{jobId}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID jobId,@RequestAttribute("userId") UUID userId,@RequestHeader(value="Last-Event-ID",defaultValue="0") long after){return service.subscribe(jobId,userId,after);}
    @DeleteMapping("/{jobId}")
    public ResponseEntity<BaseResponse<Void>> cancel(@PathVariable UUID jobId,@RequestAttribute("userId") UUID userId){service.cancel(jobId,userId);return ResponseEntity.ok(ofSucceeded(null));}
}
