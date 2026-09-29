package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Where one member of a conversation stands: how far they read and what is still unread. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceMemberInboxState {
    private UUID userId;
    /** The sequence of the last message they read; 0 when they never read any. */
    private Long readSequenceNo;
    /** Messages after that marker that somebody else sent. */
    private Long unreadCount;
    /** The newest sequence in the conversation, the same for every member; null when empty. */
    private Long lastSequenceNo;
}
