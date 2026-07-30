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
    @Override public List<HotelProfile> findHotelsByOrganization(UUID id){return mapper.findHotelsByOrganization(id);}
    @Override public List<HotelProfile> findHotelsPublic(String q,int l,int o){return mapper.findHotelsPublic(q,l,o);}
    @Override public List<HotelProfile> findHotelsAdmin(String q,String s,int l,int o){return mapper.findHotelsAdmin(q,s,l,o);}
    @Override public int insertRoomType(RoomType v){return mapper.insertRoomType(v);} @Override public int updateRoomType(RoomType v){return mapper.updateRoomType(v);}
    @Override public Optional<RoomType> findRoomType(UUID id){return Optional.ofNullable(mapper.findRoomTypeById(id));}
    @Override public List<RoomType> findRoomTypes(UUID id,boolean x){return mapper.findRoomTypesByHotel(id,x);}
    @Override public int insertRatePlan(RatePlan v){return mapper.insertRatePlan(v);} @Override public int updateRatePlan(RatePlan v){return mapper.updateRatePlan(v);}
    @Override public Optional<RatePlan> findRatePlan(UUID id){return Optional.ofNullable(mapper.findRatePlanById(id));}
    @Override public List<RatePlan> findRatePlans(UUID id,boolean x){return mapper.findRatePlansByRoomType(id,x);}
    @Override public int upsertInventoryRange(UUID r,LocalDate s,LocalDate e,Integer t,Integer b,Boolean stop,BigDecimal p,Integer m,Boolean ca,Boolean cd,UUID a,LocalDateTime n){return mapper.upsertInventoryRange(r,s,e,t,b,stop,p,m,ca,cd,a,n);}
    @Override public List<RoomInventoryDaily> findInventory(UUID r,LocalDate s,LocalDate e){return mapper.findInventory(r,s,e);}
    @Override public List<HotelAvailabilityDay> findAvailability(UUID h,UUID r,UUID p,LocalDate i,LocalDate o){return mapper.findAvailability(h,r,p,i,o);}
    @Override public int reserveInventory(UUID r,LocalDate i,LocalDate o,int q,UUID a,LocalDateTime n){return mapper.reserveInventory(r,i,o,q,a,n);}
    @Override public int confirmReservedInventory(UUID r,LocalDate i,LocalDate o,int q,UUID a,LocalDateTime n){return mapper.confirmReservedInventory(r,i,o,q,a,n);}
    @Override public int releaseInventory(UUID r,LocalDate i,LocalDate o,int q,boolean f,UUID a,LocalDateTime n){return mapper.releaseInventory(r,i,o,q,f,a,n);}
    @Override public int insertBooking(HotelBooking v){return mapper.insertBooking(v);} @Override public int insertBookingItem(HotelBookingItem v){return mapper.insertBookingItem(v);}
    @Override public Optional<HotelBooking> findBooking(UUID id){return Optional.ofNullable(mapper.findBookingById(id));}
    @Override public List<HotelBookingItem> findBookingItems(UUID id){return mapper.findBookingItems(id);}
    @Override public List<HotelBooking> findBookingsByUser(UUID u,int l,int o){return mapper.findBookingsByUser(u,l,o);}
    @Override public List<HotelBooking> findBookingsByOrganization(UUID g,String s,int l,int o){return mapper.findBookingsByOrganization(g,s,l,o);}
    @Override public List<HotelBooking> findBookingsAdmin(String q,String s,int l,int o){return mapper.findBookingsAdmin(q,s,l,o);}
    @Override public int updateBookingStatus(UUID id,long v,String s,String p,String r,LocalDateTime c,UUID a,LocalDateTime n){return mapper.updateBookingStatus(id,v,s,p,r,c,a,n);}
}
