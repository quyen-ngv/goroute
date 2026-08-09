package com.ds.goroute.repository;

import com.ds.goroute.entity.MarketplaceAuditEvent;
import com.ds.goroute.entity.MarketplaceEntityVersion;

import java.util.List;
import java.util.UUID;

public interface MarketplaceHistoryRepository {
    /**
     * Atomically appends the next immutable history version.  A false result
     * means another transaction won the same version number and the caller
     * must retry with a fresh read of the latest version.
     */
    boolean tryInsertNextVersion(MarketplaceEntityVersion version);
    int insertAuditEvent(MarketplaceAuditEvent event);
    List<MarketplaceEntityVersion> findVersions(String entityType, UUID entityId, int limit, int offset);
}
