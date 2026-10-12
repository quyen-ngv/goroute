package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AiApiCallLogRequest;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.service.AiCallLogService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records the worker's model calls for one turn in {@code ai_api_calls}. Chat text is private
 * (the console cannot read trip chats), so prompts and answers are kept only while
 * {@code ASSISTANT.LOG_PAYLOADS} is on (the beta); otherwise only model, tokens, cost and timing.
 */
@Service
@RequiredArgsConstructor
public class AssistantCallLogService {

    static final String FEATURE = "ASSISTANT";

    private final AssistantRepository repository;
    private final AiCallLogService aiCallLogService;
    private final BusinessConfigService businessConfig;

    public void record(UUID turnId, AiApiCallLogRequest call) {
        AssistantTurn turn = repository.findTurn(turnId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Assistant turn not found"));
        call.setFeature(FEATURE);
        call.setTraceId(turnId.toString());
        if (!businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_LOG_PAYLOADS)) {
            call.setRequest(null);
            call.setResponse(null);
            call.setError(null);
        }
        aiCallLogService.record(call, turn.getUserId());
    }
}
