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
}
