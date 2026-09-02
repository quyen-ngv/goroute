package com.ds.goroute.repository;

import com.ds.goroute.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityCommerceRepository {
 int insertProduct(MarketplaceActivityProduct v);int updateProduct(MarketplaceActivityProduct v);Optional<MarketplaceActivityProduct> findProduct(UUID id);Optional<MarketplaceActivityProduct> findPublicProduct(UUID id);List<MarketplaceActivityProduct> findProductsByOrganization(UUID id);List<MarketplaceActivityProduct> findProductsPublic(String q,int l,int o);List<MarketplaceActivityProduct> findProductsAdmin(String q,String s,int l,int o);
 int insertPackage(ActivityPackage v);int updatePackage(ActivityPackage v);Optional<ActivityPackage> findPackage(UUID id);List<ActivityPackage> findPackages(UUID id,boolean all);
 int insertSlot(ActivitySlot v);int updateSlot(ActivitySlot v);Optional<ActivitySlot> findSlot(UUID id);List<ActivitySlot> findSlots(UUID id,LocalDateTime from,boolean all);int reserveSlot(UUID id,int q,UUID a,LocalDateTime n);int confirmSlot(UUID id,int q,UUID a,LocalDateTime n);int releaseSlot(UUID id,int q,boolean r,UUID a,LocalDateTime n);
 int insertOrder(ActivityOrder v);int insertOrderItem(ActivityOrderItem v);Optional<ActivityOrder> findOrder(UUID id);Optional<ActivityOrder> findOrderByUserAndIdempotencyKey(UUID userId,String idempotencyKey);List<ActivityOrder> findExpiredPendingOrders(LocalDateTime now,int limit);int expireOrderHold(UUID id,long expectedVersion,UUID actor,LocalDateTime now);Optional<ActivityOrderItem> findOrderItem(UUID id);List<ActivityOrder> findOrdersByUser(UUID id,int l,int o);List<ActivityOrder> findOrdersByOrganization(UUID id,String s,List<UUID> productIds,int l,int o);long countOrdersByOrganizationFiltered(UUID id,String s,List<UUID> productIds);Optional<ActivityOrder> findOrderByVoucherCode(UUID organizationId,String voucherCode);long countActiveOrdersForSlot(UUID slotId);int assignVoucher(UUID id,String voucherCode,LocalDateTime now);int updateOrderSlot(UUID id,long expectedVersion,UUID slotId,java.math.BigDecimal subtotal,java.math.BigDecimal total,String snapshot,UUID actor,LocalDateTime now);int updateOrderItemPrice(UUID id,java.math.BigDecimal unitPrice,java.math.BigDecimal totalPrice);int markRedeemed(UUID id,UUID actor,LocalDateTime now);int voidVoucher(UUID id,LocalDateTime now);List<ActivityOrder> findOrdersAdmin(String q,String s,int l,int o);int updateOrderStatus(UUID id,long v,String s,Boolean guestCharged,UUID a,LocalDateTime n);
 long countOrdersByOrganization(UUID organizationId,String status);long countOrdersStartingOn(UUID organizationId,java.time.LocalDate day);
}
