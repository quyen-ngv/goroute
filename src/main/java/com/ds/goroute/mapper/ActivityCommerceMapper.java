package com.ds.goroute.mapper;

import com.ds.goroute.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityCommerceMapper {
    int insertProduct(MarketplaceActivityProduct product); int updateProduct(MarketplaceActivityProduct product);
    MarketplaceActivityProduct findProduct(@Param("id") UUID id);
    MarketplaceActivityProduct findPublicProduct(@Param("id") UUID id);
    List<MarketplaceActivityProduct> findProductsByOrganization(@Param("organizationId") UUID organizationId);
    List<MarketplaceActivityProduct> findProductsPublic(@Param("query") String query,@Param("activityTypes") List<String> activityTypes,@Param("limit")int limit,@Param("offset")int offset);
    List<MarketplaceActivityProduct> findSimilarProductsPublic(@Param("excludeId") UUID excludeId,@Param("activityTypes") List<String> activityTypes,@Param("lat") Double lat,@Param("lng") Double lng,@Param("limit") int limit);
    List<MarketplaceActivityProduct> findProductsAdmin(@Param("query")String query,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
    int insertPackage(ActivityPackage value); int updatePackage(ActivityPackage value); ActivityPackage findPackage(@Param("id")UUID id);
    List<ActivityPackage> findPackages(@Param("activityId")UUID activityId,@Param("includeDisabled")boolean includeDisabled);
    int insertSlot(ActivitySlot value); int updateSlot(ActivitySlot value); ActivitySlot findSlot(@Param("id")UUID id);
    List<ActivitySlot> findSlots(@Param("packageId")UUID packageId,@Param("from")LocalDateTime from,@Param("includeDisabled")boolean includeDisabled);
    int reserveSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int confirmSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int releaseSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("fromReserved")boolean fromReserved,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int insertOrder(ActivityOrder value); int insertOrderItem(ActivityOrderItem value); ActivityOrder findOrder(@Param("id")UUID id); ActivityOrder findOrderByUserAndIdempotencyKey(@Param("userId")UUID userId,@Param("idempotencyKey")String idempotencyKey);
    List<ActivityOrder> findExpiredPendingOrders(@Param("now")LocalDateTime now,@Param("limit")int limit); int expireOrderHold(@Param("id")UUID id,@Param("expectedVersion")long expectedVersion,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    ActivityOrderItem findOrderItem(@Param("orderId")UUID orderId);
    List<ActivityOrder> findOrdersByUser(@Param("userId")UUID userId,@Param("limit")int limit,@Param("offset")int offset);
    List<ActivityOrder> findOrdersByOrganization(@Param("organizationId")UUID organizationId,@Param("status")String status,@Param("productIds")List<UUID> productIds,@Param("limit")int limit,@Param("offset")int offset);
    long countOrdersByOrganizationFiltered(@Param("organizationId")UUID organizationId,@Param("status")String status,@Param("productIds")List<UUID> productIds);
    long countActiveOrdersForSlot(@Param("slotId")UUID slotId);
    ActivityOrder findOrderByVoucherCode(@Param("organizationId")UUID organizationId,@Param("voucherCode")String voucherCode);
    int updateOrderSlot(@Param("id")UUID id,@Param("expectedVersion")long expectedVersion,@Param("slotId")UUID slotId,@Param("subtotal")java.math.BigDecimal subtotal,@Param("total")java.math.BigDecimal total,@Param("snapshot")String snapshot,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int updateOrderItemPrice(@Param("id")UUID id,@Param("unitPrice")java.math.BigDecimal unitPrice,@Param("totalPrice")java.math.BigDecimal totalPrice);
    int assignVoucher(@Param("id")UUID id,@Param("voucherCode")String voucherCode,@Param("now")LocalDateTime now);
    int markRedeemed(@Param("id")UUID id,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int voidVoucher(@Param("id")UUID id,@Param("now")LocalDateTime now);
    List<ActivityOrder> findOrdersAdmin(@Param("query")String query,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
    int updateOrderStatus(@Param("id")UUID id,@Param("expectedVersion")long expectedVersion,@Param("status")String status,@Param("guestCharged")Boolean guestCharged,
                          @Param("actor")UUID actor,@Param("now")LocalDateTime now);
    long countOrdersByOrganization(@Param("organizationId")UUID organizationId,@Param("status")String status);
    long countOrdersStartingOn(@Param("organizationId")UUID organizationId,@Param("day")LocalDate day);
}
