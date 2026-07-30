package com.ds.goroute.service;

import com.ds.goroute.dto.response.MarketplaceEntityVersionResponse;

import java.util.List;
import java.util.UUID;

public interface MarketplaceHistoryService {
    void record(UUID organizationId, String entityType, UUID entityId, String action,
                Object snapshot, List<String> changedFields, UUID actorUserId, String actorType, String reason);
    List<MarketplaceEntityVersionResponse> list(String entityType, UUID entityId, int page, int size);
}
