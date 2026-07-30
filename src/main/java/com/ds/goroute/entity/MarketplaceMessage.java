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
}
