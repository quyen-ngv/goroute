package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiApiCallLogRequest;
import com.ds.goroute.dto.request.AssistantFailRequest;
import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.AssistantStepRequest;
import com.ds.goroute.dto.response.AiCreditStatus;
import com.ds.goroute.service.assistant.AssistantCallLogService;
import com.ds.goroute.service.assistant.AssistantToolService;
import com.ds.goroute.service.assistant.AssistantTurnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Callbacks of the AI worker while it answers one Pengoo turn. Authenticated by the internal
 * token filter (same secret as the AI trip worker); every call is refused once the turn is over.
 */
@Validated
@RestController
@RequestMapping("/v1/api/internal/assistant/turns/{turnId}")
@RequiredArgsConstructor
public class InternalAssistantController {

    private final AssistantTurnService turnService;
    private final AssistantToolService toolService;
    private final AssistantCallLogService callLogService;

    @PostMapping("/steps")
    public ResponseEntity<BaseResponse<Void>> step(@PathVariable UUID turnId,
                                                   @Valid @RequestBody AssistantStepRequest request) {
        turnService.appendStep(turnId, request);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }

    @PostMapping("/tools/{tool}")
    public ResponseEntity<BaseResponse<Map<String, Object>>> tool(
            @PathVariable UUID turnId,
            @PathVariable @Pattern(regexp = "[a-z_]{2,40}") String tool,
            @RequestBody(required = false) Map<String, Object> arguments) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(toolService.execute(turnId, tool, arguments)));
    }

    @PostMapping("/reply")
    public ResponseEntity<BaseResponse<Map<String, Object>>> reply(@PathVariable UUID turnId,
                                                                   @Valid @RequestBody AssistantReplyRequest request) {
        UUID messageId = turnService.complete(turnId, request);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(Map.of("messageId", messageId == null ? "" : messageId.toString())));
    }

    @PostMapping("/fail")
    public ResponseEntity<BaseResponse<Void>> fail(@PathVariable UUID turnId,
                                                   @Valid @RequestBody AssistantFailRequest request) {
        turnService.fail(turnId, request.getError() == null ? "worker failure" : request.getError());
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }

    /** Logs and charges one model call; the answer tells the worker whether to stop for credit. */
    @PostMapping("/ai-calls")
    public ResponseEntity<BaseResponse<AiCreditStatus>> aiCall(@PathVariable UUID turnId,
                                                               @Valid @RequestBody AiApiCallLogRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(callLogService.record(turnId, request)));
    }
}
