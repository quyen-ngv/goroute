package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.type.MarketplacePublicationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HotelMarketplaceService {
    List<HotelProfileResponse> listPublic(HotelSearchQuery query, int page, int size);
    HotelProfileResponse getPublic(UUID hotelId);
    List<RoomTypeResponse> listPublicRooms(UUID hotelId);
    List<RatePlanResponse> listPublicRates(UUID roomTypeId);
    List<RoomInventoryResponse> getAvailability(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                LocalDate checkIn, LocalDate checkOut, Integer quantity,
                                                Integer adults, Integer children);

    List<HotelProfileResponse> partnerListHotels(UUID actor, UUID organizationId);
    HotelProfileResponse partnerCreateHotel(UUID actor, UpsertHotelRequest request);
    HotelProfileResponse partnerUpdateHotel(UUID actor, UUID hotelId, UpsertHotelRequest request);
    List<RoomTypeResponse> partnerListRooms(UUID actor, UUID hotelId);
    RoomTypeResponse partnerCreateRoom(UUID actor, UUID hotelId, UpsertRoomTypeRequest request);
    RoomTypeResponse partnerUpdateRoom(UUID actor, UUID roomId, UpsertRoomTypeRequest request);
    List<RatePlanResponse> partnerListRates(UUID actor, UUID roomId);
    RatePlanResponse partnerCreateRate(UUID actor, UUID roomId, UpsertRatePlanRequest request);
    RatePlanResponse partnerUpdateRate(UUID actor, UUID rateId, UpsertRatePlanRequest request);
    List<RoomInventoryResponse> partnerGetInventory(UUID actor, UUID roomId, LocalDate start, LocalDate end);
    List<RoomInventoryResponse> partnerUpdateInventory(UUID actor, UUID roomId, BulkUpdateRoomInventoryRequest request);
    List<RatePlanDailyRateResponse> partnerGetRateCalendar(UUID actor, UUID ratePlanId, LocalDate start, LocalDate end);
    List<RatePlanDailyRateResponse> partnerUpdateRateCalendar(UUID actor, UUID ratePlanId, BulkUpdateRatePlanCalendarRequest request);
    PageResponse<HotelBookingResponse> partnerListBookings(UUID actor, UUID organizationId, String status, UUID hotelId, int page, int size);
    HotelBookingResponse partnerGetBooking(UUID actor, UUID bookingId);
    HotelBookingResponse partnerUpdateBookingStatus(UUID actor, UUID bookingId, UpdateHotelBookingStatusRequest request);

    /**
     * Prices a stay the guest has not booked yet, with the rules createBooking would apply.
     * Never throws for an unbookable selection: it reports why, so a cart can show a stale line.
     */
    MarketplaceQuoteResponse quoteStay(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                       LocalDate checkIn, LocalDate checkOut,
                                       int quantity, int adults, int children);

    HotelBookingResponse createBooking(UUID userId, CreateHotelBookingRequest request);
    List<HotelBookingResponse> listMyBookings(UUID userId, int page, int size);
    HotelBookingResponse getMyBooking(UUID userId, UUID bookingId);
    HotelBookingResponse cancelMyBooking(UUID userId, UUID bookingId, String reason, Long expectedVersion);
    /** Result of pricing a stay change; {@code available=false} carries the reason instead of throwing. */
    record StayQuote(boolean available, String reason, java.math.BigDecimal total, String currency, int nights) {}
    StayQuote quoteStayChange(UUID bookingId, LocalDate checkIn, LocalDate checkOut, Integer adults, Integer children);
    /** Partner accepted a change request: move inventory, re-price with the current calendar, update the booking. */
    HotelBookingResponse partnerApplyStayChange(UUID actor, UUID bookingId, LocalDate checkIn, LocalDate checkOut, Integer adults, Integer children, Long expectedVersion);
    ListingReadinessResponse partnerHotelReadiness(UUID actor, UUID hotelId);

    List<RatePlanPromotionResponse> partnerListPromotions(UUID actor, UUID hotelId);
    RatePlanPromotionResponse partnerCreatePromotion(UUID actor, UUID hotelId, UpsertRatePlanPromotionRequest request);
    RatePlanPromotionResponse partnerUpdatePromotion(UUID actor, UUID hotelId, UUID promotionId, UpsertRatePlanPromotionRequest request);
    void partnerDeletePromotion(UUID actor, UUID hotelId, UUID promotionId);

    /** Policy outcome if the guest cancelled right now; the app shows it before asking for confirmation. */
    CancellationPreviewResponse previewMyCancellation(UUID userId, UUID bookingId);

    /** Ids of PENDING_PARTNER_CONFIRMATION bookings whose hold deadline has passed, oldest first. */
    List<UUID> findExpiredPendingBookingIds(java.time.LocalDateTime now, int limit);
    /** Expires one held booking in its own transaction and releases its inventory. Returns false when it was already handled. */
    boolean expirePendingBooking(UUID bookingId, java.time.LocalDateTime now);

    List<HotelProfileResponse> adminListHotels(String query, String status, int page, int size);
    HotelProfileResponse adminGetHotel(UUID hotelId);
    List<RoomTypeResponse> adminListRooms(UUID hotelId);
    List<RatePlanResponse> adminListRates(UUID roomId);
    List<RoomInventoryResponse> adminGetInventory(UUID roomId, LocalDate start, LocalDate end);
    List<RatePlanDailyRateResponse> adminGetRateCalendar(UUID ratePlanId, LocalDate start, LocalDate end);
    HotelProfileResponse adminUpdateHotelStatus(UUID actor, UUID hotelId, MarketplacePublicationStatus status, String reason, Long expectedVersion);
    List<HotelBookingResponse> adminListBookings(String query, String status, int page, int size);
    HotelBookingResponse adminGetBooking(UUID bookingId);
    HotelBookingResponse adminUpdateBookingStatus(UUID actor, UUID bookingId, UpdateHotelBookingStatusRequest request);
    HotelProfileResponse adminCreateHotel(UUID actor, UpsertHotelRequest request);
    HotelProfileResponse adminUpdateHotel(UUID actor, UUID hotelId, UpsertHotelRequest request);
    RoomTypeResponse adminCreateRoom(UUID actor, UUID hotelId, UpsertRoomTypeRequest request);
    RoomTypeResponse adminUpdateRoom(UUID actor, UUID roomId, UpsertRoomTypeRequest request);
    RatePlanResponse adminCreateRate(UUID actor, UUID roomId, UpsertRatePlanRequest request);
    RatePlanResponse adminUpdateRate(UUID actor, UUID rateId, UpsertRatePlanRequest request);
    List<RoomInventoryResponse> adminUpdateInventory(UUID actor, UUID roomId, BulkUpdateRoomInventoryRequest request);
    List<RatePlanDailyRateResponse> adminUpdateRateCalendar(UUID actor, UUID ratePlanId, BulkUpdateRatePlanCalendarRequest request);
}
