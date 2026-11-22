package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One question to Pengoo and its progress (table {@code assistant_turns}, V228). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantTurn {
    private UUID id;
    private UUID conversationId;
    private UUID tripId;
    /** Who asked; the tools act with this person's permissions. */
    private UUID userId;
    private UUID triggerMessageId;
    private UUID replyMessageId;
    /** {@link com.ds.goroute.type.AssistantTurnStatus} name. */
    private String status;
    /** Whether the question counts against the asker's daily allowance. */
    private boolean counted;
    private String locale;
    private String question;
    /** JSON array of steps, each {key, params, text, at}. */
    private String steps;
    private String error;
    private Integer inputTokens;
    private Integer outputTokens;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    /** Joined from users; not a column. */
    private String userName;
}
