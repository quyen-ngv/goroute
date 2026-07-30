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
}
