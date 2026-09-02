package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MarketplaceMessageTemplate;
import com.ds.goroute.mapper.MarketplaceMessageTemplateMapper;
import com.ds.goroute.repository.MarketplaceMessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MarketplaceMessageTemplateRepositoryImpl implements MarketplaceMessageTemplateRepository {
    private final MarketplaceMessageTemplateMapper mapper;

    @Override public int insert(MarketplaceMessageTemplate template) { return mapper.insert(template); }
    @Override public int update(MarketplaceMessageTemplate template) { return mapper.update(template); }
    @Override public int delete(UUID id, UUID organizationId) { return mapper.delete(id, organizationId); }
    @Override public Optional<MarketplaceMessageTemplate> findById(UUID id, UUID organizationId) {
        return Optional.ofNullable(mapper.findById(id, organizationId));
    }
    @Override public List<MarketplaceMessageTemplate> findByOrganization(UUID organizationId, int limit) {
        return mapper.findByOrganization(organizationId, limit);
    }
    @Override public int countByOrganization(UUID organizationId) { return mapper.countByOrganization(organizationId); }
}
