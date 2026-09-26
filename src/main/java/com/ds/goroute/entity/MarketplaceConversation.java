package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceConversation {
 private UUID id;private String conversationType;private UUID organizationId;private UUID hotelBookingId;private UUID activityOrderId;
 private String status;private UUID assignedMemberId;private String organizationName;private String bookingCode;private String orderCode;
 private String lastMessageContent;private Long unreadCount;private LocalDateTime lastMessageAt;private LocalDateTime createdAt;private LocalDateTime updatedAt;

    /** The trip this is the group chat of, {@code null} for every other kind. */
    private UUID tripId;
    private String tripName;
    private String tripCoverImageUrl;

    /** Kept at the top of the thread for everyone; {@code null} when nothing is pinned. */
    private UUID pinnedMessageId;

    /**
     * Enough of the last message to draw an inbox row without opening the thread: who said
     * it and what kind it was, so a photo reads as a photo rather than as an empty line.
     */
    private UUID lastMessageSenderId;
    private String lastMessageSenderName;
    private String lastMessageType;

    /** How many people are in it, for the "N members" subtitle of a group. */
    private Long memberCount;

    /** When the viewer's mute expires; {@code null} when they never muted it. */
    private LocalDateTime mutedUntil;
}
