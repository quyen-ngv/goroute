package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetupChatServiceImpl implements MeetupChatService {

    static final String ROLE_CREATOR = "CREATOR";
    static final String ROLE_PARTICIPANT = "PARTICIPANT";

    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final MeetupRepository meetups;
    private final MeetupAccess access;
    private final UserRepository users;

    @Override
    @Transactional
    public MarketplaceConversationResponse open(UUID actor, UUID meetupId) {
        access.requireEnabled(actor);
        Meetup meetup = meetups.findById(meetupId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.MEETUP_NOT_FOUND, "Meetup not found"));
        requireMember(meetup, actor);
        MarketplaceConversation conversation = ensureConversation(meetup);
        reconcile(conversation.getId(), meetup, false);
        return chatService.get(actor, conversation.getId());
    }

    @Override
    @Transactional
    public void onMeetupCreated(UUID meetupId) {
        meetups.findById(meetupId).ifPresent(this::ensureConversation);
    }

    @Override
    @Transactional
    public void onMembershipChanged(UUID meetupId) {
        chatRepository.findByMeetup(meetupId, null).ifPresent(conversation ->
                meetups.findById(meetupId).ifPresent(meetup -> reconcile(conversation.getId(), meetup, true)));
    }

    @Override
    @Transactional
    public void onMeetupClosed(UUID meetupId) {
        chatRepository.findByMeetup(meetupId, null).ifPresent(conversation ->
                chatRepository.updateConversation(conversation.getId(),
                        MarketplaceConversationStatus.ARCHIVED.name(), null, false, LocalDateTime.now()));
    }

    private void requireMember(Meetup meetup, UUID userId) {
        boolean member = userId != null && (meetup.getHostUserId().equals(userId)
                || meetups.findParticipant(meetup.getId(), userId)
                        .filter(row -> row.getStatus() == MeetupParticipantStatus.JOINED)
                        .isPresent());
        if (!member) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You have not joined this meetup");
        }
    }

    /**
     * Find or create, with the unique index on {@code meetup_id} as the arbiter of a race
     * between two first opens (the same approach as the trip chat).
     */
    private MarketplaceConversation ensureConversation(Meetup meetup) {
        Optional<MarketplaceConversation> existing = chatRepository.findByMeetup(meetup.getId(), null);
        if (existing.isPresent()) return existing.get();

        LocalDateTime now = LocalDateTime.now();
        MarketplaceConversation created = MarketplaceConversation.builder()
                .id(UUID.randomUUID())
                .conversationType(MarketplaceConversationType.MEETUP.name())
                .meetupId(meetup.getId())
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            chatRepository.insertConversation(created);
        } catch (DataIntegrityViolationException raced) {
            return chatRepository.findByMeetup(meetup.getId(), null)
                    .orElseThrow(() -> raced);
        }
        // Whoever is already in the meetup is in the chat from its first moment, and nobody
        // "joined" it: the thread opens with one line saying it exists.
        desiredMembers(meetup).forEach((userId, role) -> chatRepository.addMember(created.getId(), userId, role, now));
        chatService.postSystemMessage(created.getId(), "CONVERSATION_CREATED", null, null);
        return created;
    }

    /**
     * Brings the mirrored member list back in line with the meetup's.
     *
     * @param announce whether arrivals and departures get a line in the thread; false on open,
     *                 where the change was announced when it happened or was never a change.
     */
    private void reconcile(UUID conversationId, Meetup meetup, boolean announce) {
        Map<UUID, String> desired = desiredMembers(meetup);
        Set<UUID> current = new LinkedHashSet<>(chatRepository.findActiveMemberIds(conversationId));

        LocalDateTime now = LocalDateTime.now();
        List<UUID> joined = new ArrayList<>();
        desired.forEach((userId, role) -> {
            if (current.contains(userId)) return;
            chatRepository.addMember(conversationId, userId, role, now);
            joined.add(userId);
        });
        List<UUID> left = current.stream().filter(userId -> !desired.containsKey(userId)).toList();
        for (UUID userId : left) {
            chatRepository.markMemberLeft(conversationId, userId, now);
        }
        if (!announce) return;
        joined.forEach(userId -> chatService.postSystemMessage(conversationId, "MEMBER_JOINED", userId, nameOf(userId)));
        left.forEach(userId -> chatService.postSystemMessage(conversationId, "MEMBER_LEFT", userId, nameOf(userId)));
    }

    /** The host first, then every JOINED participant, oldest request first. */
    private Map<UUID, String> desiredMembers(Meetup meetup) {
        Map<UUID, String> members = new LinkedHashMap<>();
        members.put(meetup.getHostUserId(), ROLE_CREATOR);
        for (MeetupParticipant row : meetups.findParticipants(meetup.getId(),
                List.of(MeetupParticipantStatus.JOINED), MeetupServiceImpl.PARTICIPANT_LIST_LIMIT)) {
            members.putIfAbsent(row.getUserId(), ROLE_PARTICIPANT);
        }
        return members;
    }

    private String nameOf(UUID userId) {
        return users.findById(userId)
                .map(user -> user.getFullName() == null ? user.getUsername() : user.getFullName())
                .orElse(null);
    }
}
