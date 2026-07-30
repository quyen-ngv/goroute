package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HotelMarketplaceService {
    List<HotelProfileResponse> listPublic(String query, int page, int size);
    HotelProfileResponse getPublic(UUID hotelId);
    List<RoomTypeResponse> listPublicRooms(UUID hotelId);
    List<RatePlanResponse> listPublicRates(UUID roomTypeId);
    List<RoomInventoryResponse> getAvailability(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                LocalDate checkIn, LocalDate checkOut);

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
    List<HotelBookingResponse> partnerListBookings(UUID actor, UUID organizationId, String status, int page, int size);
    HotelBookingResponse partnerGetBooking(UUID actor, UUID bookingId);
    HotelBookingResponse partnerUpdateBookingStatus(UUID actor, UUID bookingId, UpdateHotelBookingStatusRequest request);

    HotelBookingResponse createBooking(UUID userId, CreateHotelBookingRequest request);
    List<HotelBookingResponse> listMyBookings(UUID userId, int page, int size);
    HotelBookingResponse getMyBooking(UUID userId, UUID bookingId);
    HotelBookingResponse cancelMyBooking(UUID userId, UUID bookingId, String reason, Long expectedVersion);

    List<HotelProfileResponse> adminListHotels(String query, String status, int page, int size);
    HotelProfileResponse adminGetHotel(UUID hotelId);
    List<RoomTypeResponse> adminListRooms(UUID hotelId);
    List<RatePlanResponse> adminListRates(UUID roomId);
    List<RoomInventoryResponse> adminGetInventory(UUID roomId, LocalDate start, LocalDate end);
    HotelProfileResponse adminUpdateHotelStatus(UUID hotelId, String status, String reason);
    List<HotelBookingResponse> adminListBookings(String query, String status, int page, int size);
    HotelBookingResponse adminGetBooking(UUID bookingId);
    HotelBookingResponse adminUpdateBookingStatus(UUID bookingId, UpdateHotelBookingStatusRequest request);
    HotelProfileResponse adminCreateHotel(UUID actor, UpsertHotelRequest request);
    HotelProfileResponse adminUpdateHotel(UUID actor, UUID hotelId, UpsertHotelRequest request);
    RoomTypeResponse adminCreateRoom(UUID actor, UUID hotelId, UpsertRoomTypeRequest request);
    RoomTypeResponse adminUpdateRoom(UUID actor, UUID roomId, UpsertRoomTypeRequest request);
    RatePlanResponse adminCreateRate(UUID actor, UUID roomId, UpsertRatePlanRequest request);
    RatePlanResponse adminUpdateRate(UUID actor, UUID rateId, UpsertRatePlanRequest request);
    List<RoomInventoryResponse> adminUpdateInventory(UUID actor, UUID roomId, BulkUpdateRoomInventoryRequest request);
}
