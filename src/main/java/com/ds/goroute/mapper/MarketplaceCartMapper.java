package com.ds.goroute.mapper;

import com.ds.goroute.entity.MarketplaceCartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MarketplaceCartMapper {
    /** Insert, or refresh the line the guest already has for this exact selection. */
    int upsertItem(MarketplaceCartItem item);
    MarketplaceCartItem findItem(@Param("userId") UUID userId, @Param("id") UUID id);
    MarketplaceCartItem findItemBySelection(@Param("userId") UUID userId, @Param("selectionHash") String selectionHash);
    List<MarketplaceCartItem> findItemsByUser(@Param("userId") UUID userId);
    long countItemsByUser(@Param("userId") UUID userId);
    int deleteItem(@Param("userId") UUID userId, @Param("id") UUID id);
    int deleteItemsByUser(@Param("userId") UUID userId);
}
