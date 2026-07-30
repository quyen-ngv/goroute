package com.ds.goroute.repository;

import com.ds.goroute.entity.MarketplaceAuditEvent;
import com.ds.goroute.entity.MarketplaceEntityVersion;

import java.util.List;
import java.util.UUID;

public interface MarketplaceHistoryRepository {
    void lockEntity(String entityType, UUID entityId);
    long nextVersion(String entityType, UUID entityId);
    int insertVersion(MarketplaceEntityVersion version);
    int insertAuditEvent(MarketplaceAuditEvent event);
    List<MarketplaceEntityVersion> findVersions(String entityType, UUID entityId, int limit, int offset);
}
