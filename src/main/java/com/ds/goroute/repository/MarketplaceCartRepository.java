package com.ds.goroute.repository;

import com.ds.goroute.entity.MarketplaceCartItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceCartRepository {
    int upsertItem(MarketplaceCartItem item);
    Optional<MarketplaceCartItem> findItem(UUID userId, UUID id);
    Optional<MarketplaceCartItem> findItemBySelection(UUID userId, String selectionHash);
    List<MarketplaceCartItem> findItemsByUser(UUID userId);
    long countItemsByUser(UUID userId);
    int deleteItem(UUID userId, UUID id);
    int deleteItemsByUser(UUID userId);
}
