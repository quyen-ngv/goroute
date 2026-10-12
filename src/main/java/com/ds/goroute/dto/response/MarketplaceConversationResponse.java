package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class MarketplaceConversationResponse {
 private UUID id;private String conversationType;private UUID organizationId;private UUID hotelBookingId;private UUID activityOrderId;
 private String status;private UUID assignedMemberId;private String organizationName;private String bookingCode;private String orderCode;
 private String lastMessageContent;private Long unreadCount;private LocalDateTime lastMessageAt;private LocalDateTime createdAt;private LocalDateTime updatedAt;

    /**
     * Who is in the thread, excluding nobody. A booking conversation can be titled from the
     * booking; a person-to-person one has nothing else to show, and a client that had to fetch
     * each member separately would make one request per row of the inbox.
     */
    private List<ConversationParticipantResponse> participants;

    /**
     * Pengoo in this thread for the viewer (allowance, editing rights, the turn in progress).
     * Only on a trip or assistant conversation opened on its own; null in inbox lists.
     */
    private AssistantInfoResponse assistant;

    /**
     * What to head the thread with, decided once on the server so the inbox reads the same on
     * every client: the property or product for a booking, the business for a pre-booking
     * enquiry, the trip for a group, and otherwise the other people in it.
     */
    private String title;

    /**
     * The one picture for the thread: the trip cover for a group, the other person's avatar
     * for a one-to-one. Null means the client draws initials, which is the normal case for a
     * new trip with no cover yet.
     */
    private String avatarUrl;

    /** The trip this is the group chat of, {@code null} for every other kind. */
    private UUID tripId;

    /** The meetup this is the group chat of, {@code null} for every other kind. */
    private UUID meetupId;

    /** The meetup category code (e.g. COFFEE); the client draws its emoji as the thread picture. */
    private String meetupCategory;

    /** The local event this is the group chat of, {@code null} for every other kind. */
    private UUID localEventId;

    /** The event category code (e.g. MUSIC); the client draws its icon when the event has no cover. */
    private String eventCategory;

    private UUID pinnedMessageId;
    private Long memberCount;
    private LocalDateTime mutedUntil;

    /** The sequence every other member has read to; the client marks my last
     * message "Seen" once it is at or below this. */
    private Long othersReadUpToSequence;

    /** Enough of the last message to draw the inbox row. */
    private UUID lastMessageSenderId;
    private String lastMessageSenderName;
    private String lastMessageType;
    /** Set when {@link #lastMessageType} is SHARE: the kind of item shared. */
    private String lastMessageShareType;

    @Data
    @Builder
    public static class ConversationParticipantResponse {
        private UUID userId;
        private String fullName;
        private String username;
        private String avatarUrl;
        private String memberRole;
    }
}
