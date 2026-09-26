package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceMessage {
 private UUID id;private UUID conversationId;private UUID senderUserId;private String senderName;private String senderAvatarUrl;
 private String clientMessageId;private String messageType;private String content;private String attachments;private Long sequenceNo;
 private LocalDateTime editedAt;private LocalDateTime deletedAt;private LocalDateTime createdAt;

    /** The message this one answers, {@code null} for an ordinary line. */
    private UUID replyToMessageId;

    /**
     * A copy of the quoted line, read in the same query.
     *
     * <p>Fetched with the message rather than looked up by the client: a thread of fifty
     * replies would otherwise be fifty extra round trips, and the quote has to be there the
     * moment the bubble is drawn.
     */
    private String replyToSenderName;
    private String replyToContent;
    private String replyToMessageType;
    private LocalDateTime replyToDeletedAt;
}
