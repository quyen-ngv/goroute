package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MarketplaceCartItem;
import com.ds.goroute.mapper.MarketplaceCartMapper;
import com.ds.goroute.repository.MarketplaceCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class MarketplaceCartRepositoryImpl implements MarketplaceCartRepository {
    private final MarketplaceCartMapper mapper;

    @Override public int upsertItem(MarketplaceCartItem item) { return mapper.upsertItem(item); }
    @Override public Optional<MarketplaceCartItem> findItem(UUID userId, UUID id) { return Optional.ofNullable(mapper.findItem(userId, id)); }
    @Override public Optional<MarketplaceCartItem> findItemBySelection(UUID userId, String selectionHash) { return Optional.ofNullable(mapper.findItemBySelection(userId, selectionHash)); }
    @Override public List<MarketplaceCartItem> findItemsByUser(UUID userId) { return mapper.findItemsByUser(userId); }
    @Override public long countItemsByUser(UUID userId) { return mapper.countItemsByUser(userId); }
    @Override public int deleteItem(UUID userId, UUID id) { return mapper.deleteItem(userId, id); }
    @Override public int deleteItemsByUser(UUID userId) { return mapper.deleteItemsByUser(userId); }
}
