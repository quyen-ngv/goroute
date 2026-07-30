package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.HotelMarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/api/partner/hotels") @RequiredArgsConstructor
public class PartnerHotelController {
    private final HotelMarketplaceService service;
    @GetMapping public ResponseEntity<BaseResponse<List<HotelProfileResponse>>> hotels(Authentication a,@RequestParam UUID organizationId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListHotels(user(a),organizationId)));}
    @PostMapping public ResponseEntity<BaseResponse<HotelProfileResponse>> create(Authentication a,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateHotel(user(a),r)));}
    @PutMapping("/{hotelId}") public ResponseEntity<BaseResponse<HotelProfileResponse>> update(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateHotel(user(a),hotelId,r)));}
    @GetMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<List<RoomTypeResponse>>> rooms(Authentication a,@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListRooms(user(a),hotelId)));}
    @PostMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<RoomTypeResponse>> createRoom(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateRoom(user(a),hotelId,r)));}
    @PutMapping("/rooms/{roomId}") public ResponseEntity<BaseResponse<RoomTypeResponse>> updateRoom(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateRoom(user(a),roomId,r)));}
    @GetMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<List<RatePlanResponse>>> rates(Authentication a,@PathVariable UUID roomId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListRates(user(a),roomId)));}
    @PostMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<RatePlanResponse>> createRate(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateRate(user(a),roomId,r)));}
    @PutMapping("/rates/{rateId}") public ResponseEntity<BaseResponse<RatePlanResponse>> updateRate(Authentication a,@PathVariable UUID rateId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateRate(user(a),rateId,r)));}
    @GetMapping("/rooms/{roomId}/inventory") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> inventory(Authentication a,@PathVariable UUID roomId,@RequestParam LocalDate start,@RequestParam LocalDate end){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetInventory(user(a),roomId,start,end)));}
    @PutMapping("/rooms/{roomId}/inventory") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> updateInventory(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody BulkUpdateRoomInventoryRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateInventory(user(a),roomId,r)));}
    @GetMapping("/bookings") public ResponseEntity<BaseResponse<List<HotelBookingResponse>>> bookings(Authentication a,@RequestParam UUID organizationId,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListBookings(user(a),organizationId,status,page,size)));}
    @GetMapping("/bookings/{bookingId}") public ResponseEntity<BaseResponse<HotelBookingResponse>> booking(Authentication a,@PathVariable UUID bookingId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetBooking(user(a),bookingId)));}
    @PatchMapping("/bookings/{bookingId}/status") public ResponseEntity<BaseResponse<HotelBookingResponse>> bookingStatus(Authentication a,@PathVariable UUID bookingId,@Valid @RequestBody UpdateHotelBookingStatusRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateBookingStatus(user(a),bookingId,r)));}
    private UUID user(Authentication a){if(a==null||a.getPrincipal()==null)throw new AuthenticationCredentialsNotFoundException("Authentication required");Object p=a.getPrincipal();return p instanceof UUID id?id:UUID.fromString(p.toString());}
}
