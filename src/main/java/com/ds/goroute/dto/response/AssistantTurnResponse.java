package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A snapshot of one question to Pengoo: the {@code ASSISTANT_TURN} realtime event and {@code activeTurn}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantTurnResponse {
    private UUID turnId;
    private UUID conversationId;
    private String status;
    private UUID requestedBy;
    private String requestedByName;
    private UUID triggerMessageId;
    private UUID replyMessageId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<AssistantStepResponse> steps;
}
