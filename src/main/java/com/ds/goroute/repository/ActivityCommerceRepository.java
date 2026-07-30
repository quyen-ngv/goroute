package com.ds.goroute.repository;

import com.ds.goroute.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityCommerceRepository {
 int insertProduct(MarketplaceActivityProduct v);int updateProduct(MarketplaceActivityProduct v);Optional<MarketplaceActivityProduct> findProduct(UUID id);List<MarketplaceActivityProduct> findProductsByOrganization(UUID id);List<MarketplaceActivityProduct> findProductsPublic(String q,int l,int o);List<MarketplaceActivityProduct> findProductsAdmin(String q,String s,int l,int o);
 int insertPackage(ActivityPackage v);int updatePackage(ActivityPackage v);Optional<ActivityPackage> findPackage(UUID id);List<ActivityPackage> findPackages(UUID id,boolean all);
 int insertSlot(ActivitySlot v);int updateSlot(ActivitySlot v);Optional<ActivitySlot> findSlot(UUID id);List<ActivitySlot> findSlots(UUID id,LocalDateTime from,boolean all);int reserveSlot(UUID id,int q,UUID a,LocalDateTime n);int confirmSlot(UUID id,int q,UUID a,LocalDateTime n);int releaseSlot(UUID id,int q,boolean r,UUID a,LocalDateTime n);
 int insertOrder(ActivityOrder v);int insertOrderItem(ActivityOrderItem v);Optional<ActivityOrder> findOrder(UUID id);Optional<ActivityOrderItem> findOrderItem(UUID id);List<ActivityOrder> findOrdersByUser(UUID id,int l,int o);List<ActivityOrder> findOrdersByOrganization(UUID id,String s,int l,int o);List<ActivityOrder> findOrdersAdmin(String q,String s,int l,int o);int updateOrderStatus(UUID id,long v,String s,UUID a,LocalDateTime n);
}
