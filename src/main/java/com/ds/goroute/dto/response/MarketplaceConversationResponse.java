package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class MarketplaceConversationResponse {
 private UUID id;private String conversationType;private UUID organizationId;private UUID hotelBookingId;private UUID activityOrderId;
 private String status;private UUID assignedMemberId;private String organizationName;private String bookingCode;private String orderCode;
 private String lastMessageContent;private Long unreadCount;private LocalDateTime lastMessageAt;private LocalDateTime createdAt;private LocalDateTime updatedAt;
}
