package com.ds.goroute.repository;

import com.ds.goroute.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelMarketplaceRepository {
    int insertHotel(HotelProfile hotel); int updateHotel(HotelProfile hotel); Optional<HotelProfile> findHotel(UUID id); Optional<HotelProfile> findPublicHotel(UUID id);
    List<HotelProfile> findHotelsByOrganization(UUID organizationId); List<HotelProfile> findHotelsPublic(String query,int limit,int offset);
    List<HotelProfile> findHotelsAdmin(String query,String status,int limit,int offset);
    int insertRoomType(RoomType room); int updateRoomType(RoomType room); Optional<RoomType> findRoomType(UUID id);
    List<RoomType> findRoomTypes(UUID hotelId,boolean includeDisabled);
    int insertRatePlan(RatePlan rate); int updateRatePlan(RatePlan rate); Optional<RatePlan> findRatePlan(UUID id);
    List<RatePlan> findRatePlans(UUID roomTypeId,boolean includeDisabled);
    int upsertRatePlanDailyRange(UUID ratePlanId, LocalDate start, LocalDate end, List<Integer> daysOfWeek,
                                 BigDecimal price, Boolean stopSell, Integer minStay, Integer maxStay,
                                 Boolean closedArrival, Boolean closedDeparture, Integer minAdvanceDays,
                                 Integer maxAdvanceDays, String expectedVersionsJson, String clearFieldsJson, UUID actor, LocalDateTime now);
    List<RatePlanDailyRate> findRatePlanDailyRates(UUID ratePlanId, LocalDate start, LocalDate end);
    int upsertInventoryRange(UUID roomTypeId, LocalDate start, LocalDate end, Integer total, Integer blocked, Boolean stopSell,
                             BigDecimal price, Integer minStay, Boolean closedArrival, Boolean closedDeparture, String expectedVersionsJson, UUID actor, LocalDateTime now);
    List<RoomInventoryDaily> findInventory(UUID roomTypeId,LocalDate start,LocalDate end);
    List<HotelAvailabilityDay> findAvailability(UUID hotelId,UUID roomTypeId,UUID ratePlanId,LocalDate checkIn,LocalDate checkOut);
    int reserveInventory(UUID roomTypeId,UUID ratePlanId,LocalDate checkIn,LocalDate checkOut,int quantity,UUID actor,LocalDateTime now);
    int confirmReservedInventory(UUID roomTypeId,LocalDate checkIn,LocalDate checkOut,int quantity,UUID actor,LocalDateTime now);
    int releaseInventory(UUID roomTypeId,LocalDate checkIn,LocalDate checkOut,int quantity,boolean fromReserved,UUID actor,LocalDateTime now);
    int insertBooking(HotelBooking booking); int insertBookingItem(HotelBookingItem item); Optional<HotelBooking> findBooking(UUID id); Optional<HotelBooking> findBookingByUserAndIdempotencyKey(UUID userId,String idempotencyKey);
    List<HotelBooking> findExpiredPendingBookings(LocalDateTime now,int limit); int expireBookingHold(UUID id,long expectedVersion,UUID actor,LocalDateTime now);
    List<HotelBookingItem> findBookingItems(UUID bookingId); List<HotelBooking> findBookingsByUser(UUID userId,int limit,int offset);
    List<HotelBooking> findBookingsByOrganization(UUID organizationId,String status,int limit,int offset);
    List<HotelBooking> findBookingsAdmin(String query,String status,int limit,int offset);
    int updateBookingStatus(UUID id,long expectedVersion,String bookingStatus,String paymentStatus,String reason,
                            LocalDateTime cancelledAt,UUID actor,LocalDateTime now);
}
