package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One earlier chat line as the assistant reads it for context (a read model, not a table). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantHistoryMessage {
    private UUID id;
    private UUID senderUserId;
    /** Only filled by {@code findMessage}: the message this one replies to. */
    private UUID replyToMessageId;
    private String senderName;
    private String messageType;
    private String content;
    private String attachments;
    private Long sequenceNo;
}
