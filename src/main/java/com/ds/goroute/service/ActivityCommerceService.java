package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.type.MarketplacePublicationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ActivityCommerceService {
 List<MarketplaceActivityResponse> listPublic(String query,String kind,int page,int size);
 /** Other public products of the same kind (tour or ticket), nearest first. */
 List<MarketplaceActivityResponse> listSimilarPublic(UUID id,int size);
 /** Bookable dates of a public product across its enabled packages, for the date chips. */
 List<ActivityAvailabilityDayResponse> availabilityPublic(UUID id,java.time.LocalDate from,int days);
MarketplaceActivityResponse getPublic(UUID id);List<ActivityPackageResponse> listPublicPackages(UUID id);List<ActivitySlotResponse> listPublicSlots(UUID packageId,LocalDateTime from);
 List<MarketplaceActivityResponse> partnerList(UUID actor,UUID organizationId);MarketplaceActivityResponse partnerCreate(UUID actor,UpsertMarketplaceActivityRequest request);MarketplaceActivityResponse partnerUpdate(UUID actor,UUID id,UpsertMarketplaceActivityRequest request);
 List<ActivityPackageResponse> partnerPackages(UUID actor,UUID activityId);ActivityPackageResponse partnerCreatePackage(UUID actor,UUID activityId,UpsertActivityPackageRequest request);ActivityPackageResponse partnerUpdatePackage(UUID actor,UUID packageId,UpsertActivityPackageRequest request);
 List<ActivitySlotResponse> partnerSlots(UUID actor,UUID packageId,LocalDateTime from);ActivitySlotResponse partnerCreateSlot(UUID actor,UUID packageId,UpsertActivitySlotRequest request);List<ActivitySlotResponse> partnerCreateSlots(UUID actor,UUID packageId,BulkCreateActivitySlotsRequest request);ActivitySlotResponse partnerUpdateSlot(UUID actor,UUID slotId,UpsertActivitySlotRequest request);
 record SlotQuote(boolean available,String reason,java.math.BigDecimal total,String currency){}
 SlotQuote quoteSlotChange(UUID orderId,UUID slotId);ActivityOrderResponse partnerApplySlotChange(UUID actor,UUID orderId,UUID slotId,Long expectedVersion);ListingReadinessResponse partnerProductReadiness(UUID actor,UUID id);
 List<UUID> findExpiredPendingOrderIds(LocalDateTime now,int limit);boolean expirePendingOrder(UUID orderId,LocalDateTime now);
 /**
  * Prices a slot the guest has not ordered yet, with the rules createOrder would apply.
  * Never throws for an unbookable selection: it reports why, so a cart can show a stale line.
  */
 MarketplaceQuoteResponse quoteActivity(UUID activityId,UUID packageId,UUID slotId,Map<String,Integer> unitQuantities,Integer quantity);
 ActivityOrderResponse createOrder(UUID userId,CreateActivityOrderRequest request);List<ActivityOrderResponse> listMyOrders(UUID userId,int page,int size);ActivityOrderResponse getMyOrder(UUID userId,UUID id);ActivityOrderResponse cancelMyOrder(UUID userId,UUID id,String reason,Long version);CancellationPreviewResponse previewMyCancellation(UUID userId,UUID id);
 PageResponse<ActivityOrderResponse> partnerOrders(UUID actor,UUID organizationId,String status,UUID activityId,int page,int size);ActivityOrderResponse partnerRedeem(UUID actor,UUID orderId);ActivityOrderResponse partnerRedeemByVoucher(UUID actor,UUID organizationId,String voucherCode);ActivityOrderResponse partnerOrder(UUID actor,UUID id);ActivityOrderResponse partnerOrderStatus(UUID actor,UUID id,UpdateActivityOrderStatusRequest request);
 List<MarketplaceActivityResponse> adminProducts(String query,java.util.List<String> status,java.util.List<UUID> locationImageIds,String sort,boolean descending,int page,int size);MarketplaceActivityResponse adminProductStatus(UUID actor,UUID id,MarketplacePublicationStatus status,String reason,Long expectedVersion);List<ActivityOrderResponse> adminOrders(String query,java.util.List<String> status,java.util.List<String> paymentStatus,String sort,boolean descending,int page,int size);ActivityOrderResponse adminOrderStatus(UUID actor,UUID id,UpdateActivityOrderStatusRequest request);
 MarketplaceActivityResponse adminProduct(UUID id);List<ActivityPackageResponse> adminPackages(UUID activityId);List<ActivitySlotResponse> adminSlots(UUID packageId,LocalDateTime from);ActivityOrderResponse adminOrder(UUID id);
 MarketplaceActivityResponse adminCreateProduct(UUID actor,UpsertMarketplaceActivityRequest request);MarketplaceActivityResponse adminUpdateProduct(UUID actor,UUID id,UpsertMarketplaceActivityRequest request);
 ActivityPackageResponse adminCreatePackage(UUID actor,UUID activityId,UpsertActivityPackageRequest request);ActivityPackageResponse adminUpdatePackage(UUID actor,UUID packageId,UpsertActivityPackageRequest request);
 ActivitySlotResponse adminCreateSlot(UUID actor,UUID packageId,UpsertActivitySlotRequest request);ActivitySlotResponse adminUpdateSlot(UUID actor,UUID slotId,UpsertActivitySlotRequest request);
}
