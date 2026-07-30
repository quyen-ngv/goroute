package com.ds.goroute.mapper;

import com.ds.goroute.entity.MarketplaceAuditEvent;
import com.ds.goroute.entity.MarketplaceEntityVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MarketplaceHistoryMapper {
    void lockEntity(@Param("entityType") String entityType, @Param("entityId") UUID entityId);
    long nextVersion(@Param("entityType") String entityType, @Param("entityId") UUID entityId);
    int insertVersion(MarketplaceEntityVersion version);
    int insertAuditEvent(MarketplaceAuditEvent event);
    List<MarketplaceEntityVersion> findVersions(@Param("entityType") String entityType,
                                                @Param("entityId") UUID entityId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);
}
