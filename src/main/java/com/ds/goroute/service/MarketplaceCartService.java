package com.ds.goroute.service;

import com.ds.goroute.dto.request.AddCartItemRequest;
import com.ds.goroute.dto.response.CartResponse;

import java.util.UUID;

public interface MarketplaceCartService {
    CartResponse addItem(UUID userId, AddCartItemRequest request);
    CartResponse getCart(UUID userId);
    long countItems(UUID userId);
    CartResponse removeItem(UUID userId, UUID itemId);
    CartResponse clear(UUID userId);
}
