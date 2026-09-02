package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.*;
import com.ds.goroute.mapper.HotelMarketplaceMapper;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class HotelMarketplaceRepositoryImpl implements HotelMarketplaceRepository {
    private final HotelMarketplaceMapper mapper;
    @Override public int insertHotel(HotelProfile v){return mapper.insertHotel(v);} @Override public int updateHotel(HotelProfile v){return mapper.updateHotel(v);}
    @Override public Optional<HotelProfile> findHotel(UUID id){return Optional.ofNullable(mapper.findHotelById(id));}
    @Override public Optional<HotelProfile> findPublicHotel(UUID id){return Optional.ofNullable(mapper.findPublicHotelById(id));}
    @Override public List<HotelProfile> findHotelsByOrganization(UUID id){return mapper.findHotelsByOrganization(id);}
    @Override public List<HotelProfile> findHotelsPublic(String q,String t,BigDecimal min,BigDecimal max,LocalDate in,LocalDate out,int rooms,int adults,int children,int l,int o){return mapper.findHotelsPublic(q,t,min,max,in,out,rooms,adults,children,l,o);}
    @Override public int updateBookingStay(UUID id,long v,LocalDate in,LocalDate out,int a,int c,BigDecimal sub,BigDecimal total,String snap,UUID actor,LocalDateTime now){return mapper.updateBookingStay(id,v,in,out,a,c,sub,total,snap,actor,now);}
    @Override public int updateBookingItemStay(UUID id,int a,int c,BigDecimal unit,BigDecimal total){return mapper.updateBookingItemStay(id,a,c,unit,total);}
    @Override public List<HotelProfile> findHotelsAdmin(String q,String s,int l,int o){return mapper.findHotelsAdmin(q,s,l,o);}
    @Override public int insertRoomType(RoomType v){return mapper.insertRoomType(v);} @Override public int updateRoomType(RoomType v){return mapper.updateRoomType(v);}
    @Override public Optional<RoomType> findRoomType(UUID id){return Optional.ofNullable(mapper.findRoomTypeById(id));}
    @Override public List<RoomType> findRoomTypes(UUID id,boolean x){return mapper.findRoomTypesByHotel(id,x);}
    @Override public int insertRatePlan(RatePlan v){return mapper.insertRatePlan(v);} @Override public int updateRatePlan(RatePlan v){return mapper.updateRatePlan(v);}
    @Override public Optional<RatePlan> findRatePlan(UUID id){return Optional.ofNullable(mapper.findRatePlanById(id));}
    @Override public List<RatePlan> findRatePlans(UUID id,boolean x){return mapper.findRatePlansByRoomType(id,x);}
    @Override public int upsertRatePlanDailyRange(UUID r,LocalDate s,LocalDate e,List<Integer> d,BigDecimal p,Boolean stop,Integer min,Integer max,Boolean ca,Boolean cd,Integer minAdvance,Integer maxAdvance,String versions,String clearFields,UUID a,LocalDateTime n){return mapper.upsertRatePlanDailyRange(r,s,e,d,p,stop,min,max,ca,cd,minAdvance,maxAdvance,versions,clearFields,a,n);}
    @Override public List<RatePlanDailyRate> findRatePlanDailyRates(UUID r,LocalDate s,LocalDate e){return mapper.findRatePlanDailyRates(r,s,e);}
    @Override public int upsertInventoryRange(UUID r,LocalDate s,LocalDate e,Integer t,Integer b,Boolean stop,BigDecimal p,Integer m,Boolean ca,Boolean cd,String versions,UUID a,LocalDateTime n){return mapper.upsertInventoryRange(r,s,e,t,b,stop,p,m,ca,cd,versions,a,n);}
    @Override public List<RoomInventoryDaily> findInventory(UUID r,LocalDate s,LocalDate e){return mapper.findInventory(r,s,e);}
    @Override public List<HotelAvailabilityDay> findAvailability(UUID h,UUID r,UUID p,LocalDate i,LocalDate o,LocalDate today){return mapper.findAvailability(h,r,p,i,o,today);}
    @Override public long countActiveBookingsForRatePlan(UUID id){return mapper.countActiveBookingsForRatePlan(id);}
    @Override public long countActiveBookingsForRoomType(UUID id){return mapper.countActiveBookingsForRoomType(id);}
    @Override public int reserveInventory(UUID r,UUID p,LocalDate i,LocalDate o,int q,UUID a,LocalDateTime n){return mapper.reserveInventory(r,p,i,o,q,a,n);}
    @Override public int confirmReservedInventory(UUID r,LocalDate i,LocalDate o,int q,UUID a,LocalDateTime n){return mapper.confirmReservedInventory(r,i,o,q,a,n);}
    @Override public int releaseInventory(UUID r,LocalDate i,LocalDate o,int q,boolean f,UUID a,LocalDateTime n){return mapper.releaseInventory(r,i,o,q,f,a,n);}
    @Override public int insertBooking(HotelBooking v){return mapper.insertBooking(v);} @Override public int insertBookingItem(HotelBookingItem v){return mapper.insertBookingItem(v);}
    @Override public Optional<HotelBooking> findBooking(UUID id){return Optional.ofNullable(mapper.findBookingById(id));}
    @Override public Optional<HotelBooking> findBookingByUserAndIdempotencyKey(UUID userId,String key){return Optional.ofNullable(mapper.findBookingByUserAndIdempotencyKey(userId,key));}
    @Override public List<HotelBooking> findExpiredPendingBookings(LocalDateTime now,int limit){return mapper.findExpiredPendingBookings(now,limit);}
    @Override public int expireBookingHold(UUID id,long version,UUID actor,LocalDateTime now){return mapper.expireBookingHold(id,version,actor,now);}
    @Override public List<HotelBookingItem> findBookingItems(UUID id){return mapper.findBookingItems(id);}
    @Override public List<HotelBooking> findBookingsByUser(UUID u,int l,int o){return mapper.findBookingsByUser(u,l,o);}
    @Override public List<HotelBooking> findBookingsByOrganization(UUID o,String s,List<UUID> h,int l,int off){return mapper.findBookingsByOrganization(o,s,h,l,off);}
    @Override public long countBookingsByOrganizationFiltered(UUID o,String s,List<UUID> h){return mapper.countBookingsByOrganizationFiltered(o,s,h);}
    @Override public List<HotelBooking> findBookingsAdmin(String q,String s,int l,int o){return mapper.findBookingsAdmin(q,s,l,o);}
    @Override public int updateBookingStatus(UUID id,long v,String s,String p,String r,Boolean g,LocalDateTime c,UUID a,LocalDateTime n){return mapper.updateBookingStatus(id,v,s,p,r,c,g,a,n);}
    @Override public long countBookingsByOrganization(UUID o,String s){return mapper.countBookingsByOrganization(o,s);}
    @Override public long countArrivals(UUID o,LocalDate d){return mapper.countArrivals(o,d);}
    @Override public long countDepartures(UUID o,LocalDate d){return mapper.countDepartures(o,d);}
    @Override public long countInHouse(UUID o){return mapper.countInHouse(o);}
}
