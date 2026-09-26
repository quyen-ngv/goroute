package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class MarketplaceMessageResponse {
 private UUID id;private UUID conversationId;private UUID senderUserId;private String senderName;private String senderAvatarUrl;
 private String clientMessageId;private String messageType;private String content;private List<Map<String,Object>> attachments;
 private Long sequenceNo;private LocalDateTime editedAt;private LocalDateTime deletedAt;private LocalDateTime createdAt;

    /** The quoted message, already resolved, {@code null} for an ordinary line. */
    private ReplyPreview replyTo;

    /** Reactions grouped by emoji, in a stable order, empty when there are none. */
    private List<MessageReactionResponse> reactions;

    /**
     * What a client draws above a reply bubble.
     *
     * <p>{@code content} is null once the quoted message is deleted, which is how a quote of
     * a removed message renders as "message deleted" rather than keeping it alive.
     */
    @Data
    @Builder
    public static class ReplyPreview {
        private UUID id;
        private String senderName;
        private String content;
        private String messageType;
        private boolean deleted;
    }

    @Data
    @Builder
    public static class MessageReactionResponse {
        private String emoji;
        private int count;
        /** Whether the person asking is one of the people who reacted. */
        private boolean reactedByMe;
        /** Who reacted, capped by the query; used for the tooltip. */
        private List<String> userNames;
    }
}
