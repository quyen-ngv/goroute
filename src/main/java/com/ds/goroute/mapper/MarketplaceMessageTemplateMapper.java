package com.ds.goroute.mapper;

import com.ds.goroute.entity.MarketplaceMessageTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MarketplaceMessageTemplateMapper {
    int insert(MarketplaceMessageTemplate template);
    int update(MarketplaceMessageTemplate template);
    int delete(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
    MarketplaceMessageTemplate findById(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
    List<MarketplaceMessageTemplate> findByOrganization(@Param("organizationId") UUID organizationId, @Param("limit") int limit);
    int countByOrganization(@Param("organizationId") UUID organizationId);
}
