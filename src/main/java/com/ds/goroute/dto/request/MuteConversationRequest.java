package com.ds.goroute.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Silences a thread for the person asking, and for nobody else.
 *
 * <p>{@code mutedUntil} left out while {@code muted} is true means "until I say otherwise";
 * the server stores a far date rather than a null so that one column answers the question.
 */
@Data
public class MuteConversationRequest {

    private boolean muted;

    private LocalDateTime mutedUntil;
}
