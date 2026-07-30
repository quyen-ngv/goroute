package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.HotelMarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController @RequestMapping("/v1/api/admin/marketplace-hotels") @RequiredArgsConstructor
public class AdminHotelMarketplaceController {
    private final HotelMarketplaceService service;
    @GetMapping @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<List<HotelProfileResponse>>> hotels(@RequestParam(required=false)String q,@RequestParam(required=false)String search,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListHotels(q!=null?q:search,status,page,size)));}
    @PostMapping @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','create')") public ResponseEntity<BaseResponse<HotelProfileResponse>> create(Authentication a,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.adminCreateHotel(actor(a),r)));}
    @GetMapping("/{hotelId}") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<HotelProfileResponse>> hotel(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminGetHotel(hotelId)));}
    @PutMapping("/{hotelId}") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<HotelProfileResponse>> update(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateHotel(actor(a),hotelId,r)));}
    @GetMapping("/{hotelId}/rooms") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<List<RoomTypeResponse>>> rooms(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListRooms(hotelId)));}
    @PostMapping("/{hotelId}/rooms") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','create')") public ResponseEntity<BaseResponse<RoomTypeResponse>> createRoom(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.adminCreateRoom(actor(a),hotelId,r)));}
    @PutMapping("/rooms/{roomId}") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<RoomTypeResponse>> updateRoom(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateRoom(actor(a),roomId,r)));}
    @GetMapping("/rooms/{roomId}/rates") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<List<RatePlanResponse>>> rates(@PathVariable UUID roomId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListRates(roomId)));}
    @PostMapping("/rooms/{roomId}/rates") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','create')") public ResponseEntity<BaseResponse<RatePlanResponse>> createRate(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.adminCreateRate(actor(a),roomId,r)));}
    @PutMapping("/rates/{rateId}") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<RatePlanResponse>> updateRate(Authentication a,@PathVariable UUID rateId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateRate(actor(a),rateId,r)));}
    @GetMapping("/rooms/{roomId}/inventory") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> inventory(@PathVariable UUID roomId,@RequestParam LocalDate start,@RequestParam LocalDate end){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminGetInventory(roomId,start,end)));}
    @PutMapping("/rooms/{roomId}/inventory") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> updateInventory(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody BulkUpdateRoomInventoryRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateInventory(actor(a),roomId,r)));}
    @PatchMapping("/{hotelId}/status") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<HotelProfileResponse>> status(@PathVariable UUID hotelId,@RequestParam String status,@RequestParam(required=false)String reason){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateHotelStatus(hotelId,status,reason)));}
    @GetMapping("/bookings") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<List<HotelBookingResponse>>> bookings(@RequestParam(required=false)String q,@RequestParam(required=false)String search,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListBookings(q!=null?q:search,status,page,size)));}
    @GetMapping("/bookings/{bookingId}") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','get')") public ResponseEntity<BaseResponse<HotelBookingResponse>> booking(@PathVariable UUID bookingId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminGetBooking(bookingId)));}
    @PatchMapping("/bookings/{bookingId}/status") @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-hotels','update')") public ResponseEntity<BaseResponse<HotelBookingResponse>> bookingStatus(@PathVariable UUID bookingId,@Valid @RequestBody UpdateHotelBookingStatusRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateBookingStatus(bookingId,r)));}
    private UUID actor(Authentication a){return UUID.fromString(a.getName());}
}
