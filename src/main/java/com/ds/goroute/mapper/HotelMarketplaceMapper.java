package com.ds.goroute.mapper;

import com.ds.goroute.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface HotelMarketplaceMapper {
    int insertHotel(HotelProfile hotel);
    int updateHotel(HotelProfile hotel);
    HotelProfile findHotelById(@Param("id") UUID id);
    List<HotelProfile> findHotelsByOrganization(@Param("organizationId") UUID organizationId);
    List<HotelProfile> findHotelsPublic(@Param("query") String query, @Param("limit") int limit, @Param("offset") int offset);
    List<HotelProfile> findHotelsAdmin(@Param("query") String query, @Param("status") String status,
                                       @Param("limit") int limit, @Param("offset") int offset);

    int insertRoomType(RoomType roomType);
    int updateRoomType(RoomType roomType);
    RoomType findRoomTypeById(@Param("id") UUID id);
    List<RoomType> findRoomTypesByHotel(@Param("hotelId") UUID hotelId, @Param("includeDisabled") boolean includeDisabled);

    int insertRatePlan(RatePlan ratePlan);
    int updateRatePlan(RatePlan ratePlan);
    RatePlan findRatePlanById(@Param("id") UUID id);
    List<RatePlan> findRatePlansByRoomType(@Param("roomTypeId") UUID roomTypeId, @Param("includeDisabled") boolean includeDisabled);

    int upsertInventoryRange(@Param("roomTypeId") UUID roomTypeId, @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate, @Param("totalUnits") Integer totalUnits,
                             @Param("blockedUnits") Integer blockedUnits, @Param("stopSell") Boolean stopSell,
                             @Param("priceOverride") java.math.BigDecimal priceOverride, @Param("minStay") Integer minStay,
                             @Param("closedToArrival") Boolean closedToArrival,
                             @Param("closedToDeparture") Boolean closedToDeparture,
                             @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
    List<RoomInventoryDaily> findInventory(@Param("roomTypeId") UUID roomTypeId,
                                           @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<HotelAvailabilityDay> findAvailability(@Param("hotelId") UUID hotelId, @Param("roomTypeId") UUID roomTypeId,
                                                @Param("ratePlanId") UUID ratePlanId,
                                                @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);
    int reserveInventory(@Param("roomTypeId") UUID roomTypeId, @Param("checkIn") LocalDate checkIn,
                         @Param("checkOut") LocalDate checkOut, @Param("quantity") int quantity,
                         @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
    int confirmReservedInventory(@Param("roomTypeId") UUID roomTypeId, @Param("checkIn") LocalDate checkIn,
                                 @Param("checkOut") LocalDate checkOut, @Param("quantity") int quantity,
                                 @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
    int releaseInventory(@Param("roomTypeId") UUID roomTypeId, @Param("checkIn") LocalDate checkIn,
                         @Param("checkOut") LocalDate checkOut, @Param("quantity") int quantity,
                         @Param("fromReserved") boolean fromReserved, @Param("updatedBy") UUID updatedBy,
                         @Param("updatedAt") LocalDateTime updatedAt);

    int insertBooking(HotelBooking booking);
    int insertBookingItem(HotelBookingItem item);
    HotelBooking findBookingById(@Param("id") UUID id);
    List<HotelBookingItem> findBookingItems(@Param("bookingId") UUID bookingId);
    List<HotelBooking> findBookingsByUser(@Param("userId") UUID userId, @Param("limit") int limit, @Param("offset") int offset);
    List<HotelBooking> findBookingsByOrganization(@Param("organizationId") UUID organizationId,
                                                  @Param("status") String status, @Param("limit") int limit,
                                                  @Param("offset") int offset);
    List<HotelBooking> findBookingsAdmin(@Param("query") String query, @Param("status") String status,
                                         @Param("limit") int limit, @Param("offset") int offset);
    int updateBookingStatus(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                            @Param("bookingStatus") String bookingStatus, @Param("paymentStatus") String paymentStatus,
                            @Param("cancellationReason") String cancellationReason,
                            @Param("cancelledAt") LocalDateTime cancelledAt,
                            @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
}
