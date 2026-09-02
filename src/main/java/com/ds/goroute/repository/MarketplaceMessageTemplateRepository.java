package com.ds.goroute.repository;

import com.ds.goroute.entity.MarketplaceMessageTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceMessageTemplateRepository {
    int insert(MarketplaceMessageTemplate template);
    int update(MarketplaceMessageTemplate template);
    int delete(UUID id, UUID organizationId);
    Optional<MarketplaceMessageTemplate> findById(UUID id, UUID organizationId);
    List<MarketplaceMessageTemplate> findByOrganization(UUID organizationId, int limit);
    int countByOrganization(UUID organizationId);
}
