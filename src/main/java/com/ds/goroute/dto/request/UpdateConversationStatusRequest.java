package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceConversationStatus;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateConversationStatusRequest {
 private MarketplaceConversationStatus status;
 private UUID assignedMemberId;
 /** Explicitly clear the assignee. A null assignedMemberId alone keeps the current one. */
 private boolean unassign;
}
