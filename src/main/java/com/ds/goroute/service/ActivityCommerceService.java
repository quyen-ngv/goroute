package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityCommerceService {
 List<MarketplaceActivityResponse> listPublic(String query,int page,int size);MarketplaceActivityResponse getPublic(UUID id);List<ActivityPackageResponse> listPublicPackages(UUID id);List<ActivitySlotResponse> listPublicSlots(UUID packageId,LocalDateTime from);
 List<MarketplaceActivityResponse> partnerList(UUID actor,UUID organizationId);MarketplaceActivityResponse partnerCreate(UUID actor,UpsertMarketplaceActivityRequest request);MarketplaceActivityResponse partnerUpdate(UUID actor,UUID id,UpsertMarketplaceActivityRequest request);
 List<ActivityPackageResponse> partnerPackages(UUID actor,UUID activityId);ActivityPackageResponse partnerCreatePackage(UUID actor,UUID activityId,UpsertActivityPackageRequest request);ActivityPackageResponse partnerUpdatePackage(UUID actor,UUID packageId,UpsertActivityPackageRequest request);
 List<ActivitySlotResponse> partnerSlots(UUID actor,UUID packageId,LocalDateTime from);ActivitySlotResponse partnerCreateSlot(UUID actor,UUID packageId,UpsertActivitySlotRequest request);ActivitySlotResponse partnerUpdateSlot(UUID actor,UUID slotId,UpsertActivitySlotRequest request);
 ActivityOrderResponse createOrder(UUID userId,CreateActivityOrderRequest request);List<ActivityOrderResponse> listMyOrders(UUID userId,int page,int size);ActivityOrderResponse getMyOrder(UUID userId,UUID id);ActivityOrderResponse cancelMyOrder(UUID userId,UUID id,String reason,Long version);
 List<ActivityOrderResponse> partnerOrders(UUID actor,UUID organizationId,String status,int page,int size);ActivityOrderResponse partnerOrder(UUID actor,UUID id);ActivityOrderResponse partnerOrderStatus(UUID actor,UUID id,UpdateActivityOrderStatusRequest request);
 List<MarketplaceActivityResponse> adminProducts(String query,String status,int page,int size);MarketplaceActivityResponse adminProductStatus(UUID id,String status,String reason);List<ActivityOrderResponse> adminOrders(String query,String status,int page,int size);ActivityOrderResponse adminOrderStatus(UUID id,UpdateActivityOrderStatusRequest request);
 MarketplaceActivityResponse adminProduct(UUID id);List<ActivityPackageResponse> adminPackages(UUID activityId);List<ActivitySlotResponse> adminSlots(UUID packageId,LocalDateTime from);ActivityOrderResponse adminOrder(UUID id);
 MarketplaceActivityResponse adminCreateProduct(UUID actor,UpsertMarketplaceActivityRequest request);MarketplaceActivityResponse adminUpdateProduct(UUID actor,UUID id,UpsertMarketplaceActivityRequest request);
 ActivityPackageResponse adminCreatePackage(UUID actor,UUID activityId,UpsertActivityPackageRequest request);ActivityPackageResponse adminUpdatePackage(UUID actor,UUID packageId,UpsertActivityPackageRequest request);
 ActivitySlotResponse adminCreateSlot(UUID actor,UUID packageId,UpsertActivitySlotRequest request);ActivitySlotResponse adminUpdateSlot(UUID actor,UUID slotId,UpsertActivitySlotRequest request);
}
