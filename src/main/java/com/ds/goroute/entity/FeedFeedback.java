package com.ds.goroute.entity;

import com.ds.goroute.type.FeedFeedbackKind;
import com.ds.goroute.type.FeedItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of feed_feedback (V245): a post or a person the viewer hid from their feed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedFeedback {
    private UUID id;
    private UUID userId;
    private FeedFeedbackKind kind;
    private FeedItemType itemType;
    private UUID itemId;
    private UUID targetUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
