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
    HotelProfile findPublicHotelById(@Param("id") UUID id);
    List<HotelProfile> findHotelsByOrganization(@Param("organizationId") UUID organizationId);
    List<HotelProfile> findHotelsPublic(@Param("query") String query, @Param("propertyType") String propertyType,
                                        @Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice,
                                        @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut,
                                        @Param("rooms") int rooms, @Param("adults") int adults, @Param("children") int children,
                                        @Param("limit") int limit, @Param("offset") int offset);
    int updateBookingStay(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion, @Param("checkIn") LocalDate checkIn,
                          @Param("checkOut") LocalDate checkOut, @Param("adults") int adults, @Param("children") int children,
                          @Param("subtotal") java.math.BigDecimal subtotal, @Param("total") java.math.BigDecimal total,
                          @Param("snapshot") String snapshot, @Param("actor") UUID actor, @Param("now") LocalDateTime now);
    int updateBookingItemStay(@Param("id") UUID id, @Param("adults") int adults, @Param("children") int children,
                              @Param("unitPrice") java.math.BigDecimal unitPrice, @Param("totalPrice") java.math.BigDecimal totalPrice);
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
    int upsertRatePlanDailyRange(@Param("ratePlanId") UUID ratePlanId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate,
                                 @Param("daysOfWeek") List<Integer> daysOfWeek,
                                 @Param("price") java.math.BigDecimal price,
                                 @Param("stopSell") Boolean stopSell,
                                 @Param("minStay") Integer minStay,
                                 @Param("maxStay") Integer maxStay,
                                 @Param("closedToArrival") Boolean closedToArrival,
                                 @Param("closedToDeparture") Boolean closedToDeparture,
                                 @Param("minAdvanceDays") Integer minAdvanceDays,
                                 @Param("maxAdvanceDays") Integer maxAdvanceDays,
                                 @Param("expectedVersionsJson") String expectedVersionsJson,
                                 @Param("clearFieldsJson") String clearFieldsJson,
                                 @Param("updatedBy") UUID updatedBy,
                                 @Param("updatedAt") LocalDateTime updatedAt);
    List<RatePlanDailyRate> findRatePlanDailyRates(@Param("ratePlanId") UUID ratePlanId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    int upsertInventoryRange(@Param("roomTypeId") UUID roomTypeId, @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate, @Param("totalUnits") Integer totalUnits,
                             @Param("blockedUnits") Integer blockedUnits, @Param("stopSell") Boolean stopSell,
                             @Param("priceOverride") java.math.BigDecimal priceOverride, @Param("minStay") Integer minStay,
                             @Param("closedToArrival") Boolean closedToArrival,
                             @Param("closedToDeparture") Boolean closedToDeparture,
                             @Param("expectedVersionsJson") String expectedVersionsJson,
                             @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
    List<RoomInventoryDaily> findInventory(@Param("roomTypeId") UUID roomTypeId,
                                           @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    long countActiveBookingsForRatePlan(@Param("ratePlanId") UUID ratePlanId);
    long countActiveBookingsForRoomType(@Param("roomTypeId") UUID roomTypeId);
    List<HotelAvailabilityDay> findAvailability(@Param("hotelId") UUID hotelId, @Param("roomTypeId") UUID roomTypeId,
                                                @Param("ratePlanId") UUID ratePlanId,
                                                @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut, @Param("today") LocalDate today);
    int reserveInventory(@Param("roomTypeId") UUID roomTypeId, @Param("ratePlanId") UUID ratePlanId, @Param("checkIn") LocalDate checkIn,
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
    HotelBooking findBookingByUserAndIdempotencyKey(@Param("userId") UUID userId, @Param("idempotencyKey") String idempotencyKey);
    List<HotelBooking> findExpiredPendingBookings(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int expireBookingHold(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion, @Param("actor") UUID actor, @Param("now") LocalDateTime now);
    List<HotelBookingItem> findBookingItems(@Param("bookingId") UUID bookingId);
    List<HotelBooking> findBookingsByUser(@Param("userId") UUID userId, @Param("limit") int limit, @Param("offset") int offset);
    List<HotelBooking> findBookingsByOrganization(@Param("organizationId") UUID organizationId,
                                                  @Param("status") String status, @Param("hotelIds") List<UUID> hotelIds,
                                                  @Param("limit") int limit, @Param("offset") int offset);
    long countBookingsByOrganizationFiltered(@Param("organizationId") UUID organizationId,
                                             @Param("status") String status, @Param("hotelIds") List<UUID> hotelIds);
    List<HotelBooking> findBookingsAdmin(@Param("query") String query, @Param("status") String status,
                                         @Param("limit") int limit, @Param("offset") int offset);
    int updateBookingStatus(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                            @Param("bookingStatus") String bookingStatus, @Param("paymentStatus") String paymentStatus,
                            @Param("cancellationReason") String cancellationReason,
                            @Param("cancelledAt") LocalDateTime cancelledAt, @Param("guestCharged") Boolean guestCharged,
                            @Param("updatedBy") UUID updatedBy, @Param("updatedAt") LocalDateTime updatedAt);

    long countBookingsByOrganization(@Param("organizationId") UUID organizationId, @Param("status") String status);
    long countArrivals(@Param("organizationId") UUID organizationId, @Param("day") LocalDate day);
    long countDepartures(@Param("organizationId") UUID organizationId, @Param("day") LocalDate day);
    long countInHouse(@Param("organizationId") UUID organizationId);
}
