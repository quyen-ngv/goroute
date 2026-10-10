package com.ds.goroute.localevent.service;

import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The group chat of a local event (epic 17).
 *
 * <p>Its members are everybody going to the event, mirrored into the conversation so read
 * markers and notifications have rows to hang off. Whether somebody may read the thread is asked
 * of {@code local_event_attendees}, live (see {@code MarketplaceChatRepository.isEventAttendee}),
 * so leaving takes the chat away at once. Unlike the meetup chat the mirror is kept one person
 * at a time rather than reconciled as a whole: an event can draw far more people than a meetup.
 * The caller checks who may open it; this class only keeps the thread in step.
 */
@Service
@RequiredArgsConstructor
public class LocalEventChatService {

    static final String ROLE_PARTICIPANT = "PARTICIPANT";

    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final UserRepository users;

    /** Opens the event's conversation for someone already known to be going. */
    @Transactional
    public MarketplaceConversationResponse open(UUID actor, UUID eventId) {
        MarketplaceConversation conversation = ensureConversation(eventId);
        // Quietly put back a member row the mirror lost; the arrival was announced when it happened.
        chatRepository.addMember(conversation.getId(), actor, ROLE_PARTICIPANT, LocalDateTime.now());
        return chatService.get(actor, conversation.getId());
    }

    /** Someone started going: they are in the chat, and the thread says so. */
    @Transactional
    public void onJoined(UUID eventId, UUID userId) {
        MarketplaceConversation conversation = ensureConversation(eventId);
        chatRepository.addMember(conversation.getId(), userId, ROLE_PARTICIPANT, LocalDateTime.now());
        chatService.postSystemMessage(conversation.getId(), "MEMBER_JOINED", userId, nameOf(userId));
    }

    /** Someone stopped going. Nothing to do when the event never had a chat. */
    @Transactional
    public void onLeft(UUID eventId, UUID userId) {
        chatRepository.findByLocalEvent(eventId, null).ifPresent(conversation -> {
            chatRepository.markMemberLeft(conversation.getId(), userId, LocalDateTime.now());
            chatService.postSystemMessage(conversation.getId(), "MEMBER_LEFT", userId, nameOf(userId));
        });
    }

    /**
     * Find or create, with the unique index on {@code local_event_id} as the arbiter of a race
     * between two first joins (the same approach as the trip and meetup chats).
     */
    private MarketplaceConversation ensureConversation(UUID eventId) {
        Optional<MarketplaceConversation> existing = chatRepository.findByLocalEvent(eventId, null);
        if (existing.isPresent()) return existing.get();

        LocalDateTime now = LocalDateTime.now();
        MarketplaceConversation created = MarketplaceConversation.builder()
                .id(UUID.randomUUID())
                .conversationType(MarketplaceConversationType.EVENT.name())
                .localEventId(eventId)
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            chatRepository.insertConversation(created);
        } catch (DataIntegrityViolationException raced) {
            return chatRepository.findByLocalEvent(eventId, null).orElseThrow(() -> raced);
        }
        chatService.postSystemMessage(created.getId(), "CONVERSATION_CREATED", null, null);
        return created;
    }

    private String nameOf(UUID userId) {
        return users.findById(userId)
                .map(user -> user.getFullName() == null ? user.getUsername() : user.getFullName())
                .orElse(null);
    }
}
