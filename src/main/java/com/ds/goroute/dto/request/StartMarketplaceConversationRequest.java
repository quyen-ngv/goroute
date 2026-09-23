package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceConversationType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class StartMarketplaceConversationRequest {
 @NotNull private MarketplaceConversationType conversationType;
 private UUID hotelBookingId;private UUID activityOrderId;private List<UUID> participantUserIds;

    /**
     * Message a business rather than a person, before there is any booking to hang the thread
     * off. The server picks who answers for it, because a guest has no way to know — and must
     * not be told — which staff account holds {@code CHAT_WRITE}.
     *
     * <p>Only meaningful with {@code conversationType = DIRECT}; it replaces
     * {@code participantUserIds}, which is ignored when this is set.
     */
    private UUID organizationId;
}
