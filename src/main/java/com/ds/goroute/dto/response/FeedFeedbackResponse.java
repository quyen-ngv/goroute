package com.ds.goroute.dto.response;

import com.ds.goroute.type.FeedFeedbackKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A hide that is now in force. {@code id} undoes it (DELETE /v1/api/feed/feedback/{id}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedFeedbackResponse {
    private UUID id;
    private FeedFeedbackKind kind;
    /** Null for a hidden post, which stays hidden. */
    private LocalDateTime expiresAt;
}
