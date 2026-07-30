package com.ds.goroute.mapper;

import com.ds.goroute.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityCommerceMapper {
    int insertProduct(MarketplaceActivityProduct product); int updateProduct(MarketplaceActivityProduct product);
    MarketplaceActivityProduct findProduct(@Param("id") UUID id);
    List<MarketplaceActivityProduct> findProductsByOrganization(@Param("organizationId") UUID organizationId);
    List<MarketplaceActivityProduct> findProductsPublic(@Param("query") String query,@Param("limit")int limit,@Param("offset")int offset);
    List<MarketplaceActivityProduct> findProductsAdmin(@Param("query")String query,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
    int insertPackage(ActivityPackage value); int updatePackage(ActivityPackage value); ActivityPackage findPackage(@Param("id")UUID id);
    List<ActivityPackage> findPackages(@Param("activityId")UUID activityId,@Param("includeDisabled")boolean includeDisabled);
    int insertSlot(ActivitySlot value); int updateSlot(ActivitySlot value); ActivitySlot findSlot(@Param("id")UUID id);
    List<ActivitySlot> findSlots(@Param("packageId")UUID packageId,@Param("from")LocalDateTime from,@Param("includeDisabled")boolean includeDisabled);
    int reserveSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int confirmSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int releaseSlot(@Param("id")UUID id,@Param("quantity")int quantity,@Param("fromReserved")boolean fromReserved,@Param("actor")UUID actor,@Param("now")LocalDateTime now);
    int insertOrder(ActivityOrder value); int insertOrderItem(ActivityOrderItem value); ActivityOrder findOrder(@Param("id")UUID id);
    ActivityOrderItem findOrderItem(@Param("orderId")UUID orderId);
    List<ActivityOrder> findOrdersByUser(@Param("userId")UUID userId,@Param("limit")int limit,@Param("offset")int offset);
    List<ActivityOrder> findOrdersByOrganization(@Param("organizationId")UUID organizationId,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
    List<ActivityOrder> findOrdersAdmin(@Param("query")String query,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
    int updateOrderStatus(@Param("id")UUID id,@Param("expectedVersion")long expectedVersion,@Param("status")String status,
                          @Param("actor")UUID actor,@Param("now")LocalDateTime now);
}
