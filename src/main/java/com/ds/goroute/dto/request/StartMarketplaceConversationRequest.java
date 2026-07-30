package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class StartMarketplaceConversationRequest {
 @Pattern(regexp="DIRECT|HOTEL_BOOKING|ACTIVITY_ORDER") private String conversationType;
 private UUID hotelBookingId;private UUID activityOrderId;private List<UUID> participantUserIds;
}
