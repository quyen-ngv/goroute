package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.TripChatService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripChatServiceImpl implements TripChatService {

    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final TripRepository trips;
    private final TripMemberRepository tripMembers;
    private final UserRepository users;

    @Override
    @Transactional
    public MarketplaceConversationResponse open(UUID actor, UUID tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        requireMember(tripId, actor);
        MarketplaceConversation conversation = ensureConversation(trip);
        reconcile(conversation.getId(), tripId, false);
        return chatService.get(actor, conversation.getId());
    }

    @Override
    @Transactional
    public void onTripCreated(UUID tripId) {
        trips.findById(tripId).ifPresent(this::ensureConversation);
    }

    @Override
    @Transactional
    public void onMembershipChanged(UUID tripId) {
        chatRepository.findByTrip(tripId, null)
                .ifPresent(conversation -> reconcile(conversation.getId(), tripId, true));
    }

    @Override
    @Transactional
    public void onTripDeleted(UUID tripId) {
        chatRepository.findByTrip(tripId, null).ifPresent(conversation ->
                chatRepository.updateConversation(conversation.getId(),
                        MarketplaceConversationStatus.ARCHIVED.name(), null, false, LocalDateTime.now()));
    }

    private void requireMember(UUID tripId, UUID userId) {
        boolean member = userId != null && tripMembers.findByTripIdAndUserId(tripId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
                .isPresent();
        if (!member) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a member of this trip");
        }
    }

    /**
     * Find or create, with the unique index as the arbiter.
     *
     * <p>Two members opening a new trip's chat at the same moment both find nothing and both
     * insert; one of them loses on the index and reads the winner's row. Holding a lock over
     * the check instead would mean locking a table nobody has a row in yet.
     */
    private MarketplaceConversation ensureConversation(Trip trip) {
        Optional<MarketplaceConversation> existing = chatRepository.findByTrip(trip.getId(), null);
        if (existing.isPresent()) return existing.get();

        LocalDateTime now = LocalDateTime.now();
        MarketplaceConversation created = MarketplaceConversation.builder()
                .id(UUID.randomUUID())
                .conversationType(MarketplaceConversationType.TRIP.name())
                .tripId(trip.getId())
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            chatRepository.insertConversation(created);
        } catch (DataIntegrityViolationException raced) {
            return chatRepository.findByTrip(trip.getId(), null)
                    .orElseThrow(() -> raced);
        }
        // Everybody who is already in the trip is in the chat from its first moment, and
        // none of them "joined" it: the thread opens with one line saying it exists.
        for (TripMember member : acceptedMembers(trip.getId())) {
            chatRepository.addMember(created.getId(), member.getUserId(), memberRole(member), now);
        }
        chatService.postSystemMessage(created.getId(), "CONVERSATION_CREATED", null, null);
        return created;
    }

    /**
     * Brings the mirrored member list back in line with the trip's.
     *
     * @param announce whether arrivals and departures are worth a line in the thread. False
     *                 when reconciling on open, where the change already happened and was
     *                 announced then, or was never a change at all.
     */
    private void reconcile(UUID conversationId, UUID tripId, boolean announce) {
        Set<UUID> desired = new LinkedHashSet<>();
        List<TripMember> accepted = acceptedMembers(tripId);
        accepted.forEach(member -> desired.add(member.getUserId()));
        Set<UUID> current = new LinkedHashSet<>(chatRepository.findActiveMemberIds(conversationId));

        LocalDateTime now = LocalDateTime.now();
        List<UUID> joined = new ArrayList<>();
        for (TripMember member : accepted) {
            if (current.contains(member.getUserId())) continue;
            chatRepository.addMember(conversationId, member.getUserId(), memberRole(member), now);
            joined.add(member.getUserId());
        }
        List<UUID> left = current.stream().filter(userId -> !desired.contains(userId)).toList();
        for (UUID userId : left) {
            chatRepository.markMemberLeft(conversationId, userId, now);
        }
        if (!announce) return;
        joined.forEach(userId -> chatService.postSystemMessage(conversationId, "MEMBER_JOINED", userId, nameOf(userId)));
        left.forEach(userId -> chatService.postSystemMessage(conversationId, "MEMBER_LEFT", userId, nameOf(userId)));
    }

    /** Guests without an account are trip members but not chat members: there is nobody to talk to. */
    private List<TripMember> acceptedMembers(UUID tripId) {
        return tripMembers.findByTripId(tripId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACCEPTED)
                .filter(member -> member.getUserId() != null)
                .filter(member -> !Boolean.TRUE.equals(member.getIsGuest()))
                .toList();
    }

    private String memberRole(TripMember member) {
        return member.getRole() == MemberRole.OWNER ? "CREATOR" : "PARTICIPANT";
    }

    private String nameOf(UUID userId) {
        return users.findById(userId)
                .map(user -> user.getFullName() == null ? user.getUsername() : user.getFullName())
                .orElse(null);
    }

}
