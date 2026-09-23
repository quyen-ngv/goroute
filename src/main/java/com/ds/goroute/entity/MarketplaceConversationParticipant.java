package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Someone in a conversation, with enough of their profile to head a thread.
 *
 * <p>A booking conversation can be titled from the booking, but a person-to-person one has
 * nothing to show except who is in it. Returning the members with the conversation is what
 * lets a client render a name and a face without a second call per row of the inbox.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceConversationParticipant {

    private UUID conversationId;
    private UUID userId;
    private String fullName;
    private String username;
    private String avatarUrl;
    /** {@code GUEST}, {@code HOST}, {@code CREATOR} or {@code PARTICIPANT}. */
    private String memberRole;
}
