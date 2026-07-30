package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MarketplaceAuditEvent;
import com.ds.goroute.entity.MarketplaceEntityVersion;
import com.ds.goroute.mapper.MarketplaceHistoryMapper;
import com.ds.goroute.repository.MarketplaceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MarketplaceHistoryRepositoryImpl implements MarketplaceHistoryRepository {
    private final MarketplaceHistoryMapper mapper;
    @Override public void lockEntity(String entityType, UUID entityId) { mapper.lockEntity(entityType, entityId); }
    @Override public long nextVersion(String entityType, UUID entityId) { return mapper.nextVersion(entityType, entityId); }
    @Override public int insertVersion(MarketplaceEntityVersion version) { return mapper.insertVersion(version); }
    @Override public int insertAuditEvent(MarketplaceAuditEvent event) { return mapper.insertAuditEvent(event); }
    @Override public List<MarketplaceEntityVersion> findVersions(String entityType, UUID entityId, int limit, int offset) {
        return mapper.findVersions(entityType, entityId, limit, offset);
    }
}
