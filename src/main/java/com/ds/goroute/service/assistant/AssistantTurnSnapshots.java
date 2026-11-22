package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.AssistantStepResponse;
import com.ds.goroute.dto.response.AssistantTurnResponse;
import com.ds.goroute.entity.AssistantTurn;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Maps a stored turn to the snapshot the app draws (live "thinking" bubble and its replay). */
@Component
@RequiredArgsConstructor
public class AssistantTurnSnapshots {

    private final ObjectMapper objectMapper;

    public AssistantTurnResponse snapshot(AssistantTurn turn) {
        return AssistantTurnResponse.builder()
                .turnId(turn.getId())
                .conversationId(turn.getConversationId())
                .status(turn.getStatus())
                .requestedBy(turn.getUserId())
                .requestedByName(turn.getUserName())
                .triggerMessageId(turn.getTriggerMessageId())
                .replyMessageId(turn.getReplyMessageId())
                .startedAt(turn.getStartedAt())
                .finishedAt(turn.getFinishedAt())
                .steps(steps(turn.getSteps()))
                .build();
    }

    public List<AssistantStepResponse> steps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssistantStepResponse>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** The snapshot as the realtime payload map. */
    public Map<String, Object> asEvent(AssistantTurn turn) {
        return objectMapper.convertValue(snapshot(turn), new TypeReference<Map<String, Object>>() {
        });
    }
}
