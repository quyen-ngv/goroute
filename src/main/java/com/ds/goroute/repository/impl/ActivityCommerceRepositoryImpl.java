package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.*;
import com.ds.goroute.mapper.ActivityCommerceMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ActivityCommerceRepositoryImpl implements ActivityCommerceRepository {
 private final ActivityCommerceMapper m;
 public int insertProduct(MarketplaceActivityProduct v){return m.insertProduct(v);}public int updateProduct(MarketplaceActivityProduct v){return m.updateProduct(v);}public Optional<MarketplaceActivityProduct> findProduct(UUID id){return Optional.ofNullable(m.findProduct(id));}public List<MarketplaceActivityProduct> findProductsByOrganization(UUID id){return m.findProductsByOrganization(id);}public List<MarketplaceActivityProduct> findProductsPublic(String q,int l,int o){return m.findProductsPublic(q,l,o);}public List<MarketplaceActivityProduct> findProductsAdmin(String q,String s,int l,int o){return m.findProductsAdmin(q,s,l,o);}
 public int insertPackage(ActivityPackage v){return m.insertPackage(v);}public int updatePackage(ActivityPackage v){return m.updatePackage(v);}public Optional<ActivityPackage> findPackage(UUID id){return Optional.ofNullable(m.findPackage(id));}public List<ActivityPackage> findPackages(UUID id,boolean a){return m.findPackages(id,a);}
 public int insertSlot(ActivitySlot v){return m.insertSlot(v);}public int updateSlot(ActivitySlot v){return m.updateSlot(v);}public Optional<ActivitySlot> findSlot(UUID id){return Optional.ofNullable(m.findSlot(id));}public List<ActivitySlot> findSlots(UUID id,LocalDateTime f,boolean a){return m.findSlots(id,f,a);}public int reserveSlot(UUID id,int q,UUID a,LocalDateTime n){return m.reserveSlot(id,q,a,n);}public int confirmSlot(UUID id,int q,UUID a,LocalDateTime n){return m.confirmSlot(id,q,a,n);}public int releaseSlot(UUID id,int q,boolean r,UUID a,LocalDateTime n){return m.releaseSlot(id,q,r,a,n);}
 public int insertOrder(ActivityOrder v){return m.insertOrder(v);}public int insertOrderItem(ActivityOrderItem v){return m.insertOrderItem(v);}public Optional<ActivityOrder> findOrder(UUID id){return Optional.ofNullable(m.findOrder(id));}public Optional<ActivityOrderItem> findOrderItem(UUID id){return Optional.ofNullable(m.findOrderItem(id));}public List<ActivityOrder> findOrdersByUser(UUID id,int l,int o){return m.findOrdersByUser(id,l,o);}public List<ActivityOrder> findOrdersByOrganization(UUID id,String s,int l,int o){return m.findOrdersByOrganization(id,s,l,o);}public List<ActivityOrder> findOrdersAdmin(String q,String s,int l,int o){return m.findOrdersAdmin(q,s,l,o);}public int updateOrderStatus(UUID id,long v,String s,UUID a,LocalDateTime n){return m.updateOrderStatus(id,v,s,a,n);}
}
