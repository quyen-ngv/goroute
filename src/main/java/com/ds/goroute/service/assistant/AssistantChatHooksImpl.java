package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.AssistantInfoResponse;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ChatAssistantHooks;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.type.MarketplaceConversationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistantChatHooksImpl implements ChatAssistantHooks {

    private final AssistantAccessService access;
    private final AssistantRepository repository;
    private final AssistantTurnSnapshots snapshots;
    private final TripRepository trips;
    private final TripAccessGuard tripAccessGuard;

    @Override
    public AssistantInfoResponse info(UUID viewerId, MarketplaceConversation conversation) {
        if (viewerId == null || conversation == null) return null;
        boolean isTrip = MarketplaceConversationType.TRIP.name().equals(conversation.getConversationType());
        boolean isDirect = MarketplaceConversationType.ASSISTANT.name().equals(conversation.getConversationType());
        if (!isTrip && !isDirect) return null;
        AssistantTurn active = repository.findRunningTurn(conversation.getId())
                .or(() -> repository.findNextQueuedTurn(conversation.getId()))
                .orElse(null);
        return AssistantInfoResponse.builder()
                .userId(AssistantIdentity.BOT_USER_ID)
                .name(AssistantIdentity.NAME)
                .username(AssistantIdentity.USERNAME)
                .available(access.isAvailable(viewerId))
                .dailyLimit(access.dailyLimit(viewerId))
                .usedToday(access.usedToday(viewerId))
                .canEditTrip(isTrip && canEdit(conversation.getTripId(), viewerId))
                .activeTurn(active == null ? null : snapshots.snapshot(active))
                .build();
    }

    private boolean canEdit(UUID tripId, UUID viewerId) {
        return tripId != null && trips.findById(tripId)
                .map(trip -> tripAccessGuard.canEdit(trip, viewerId))
                .orElse(false);
    }
}
