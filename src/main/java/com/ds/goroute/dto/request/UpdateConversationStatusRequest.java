package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceConversationStatus;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateConversationStatusRequest {
 private MarketplaceConversationStatus status;
 private UUID assignedMemberId;
}
