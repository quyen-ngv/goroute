package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.HotelMarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/api/public/hotels") @RequiredArgsConstructor
public class PublicHotelController {
    private final HotelMarketplaceService service;
    @GetMapping public ResponseEntity<BaseResponse<List<HotelProfileResponse>>> list(@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublic(q,page,size)));}
    @GetMapping("/{hotelId}") public ResponseEntity<BaseResponse<HotelProfileResponse>> get(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getPublic(hotelId)));}
    @GetMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<List<RoomTypeResponse>>> rooms(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublicRooms(hotelId)));}
    @GetMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<List<RatePlanResponse>>> rates(@PathVariable UUID roomId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublicRates(roomId)));}
    @GetMapping("/{hotelId}/availability") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> availability(@PathVariable UUID hotelId,@RequestParam UUID roomTypeId,@RequestParam UUID ratePlanId,@RequestParam LocalDate checkIn,@RequestParam LocalDate checkOut){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getAvailability(hotelId,roomTypeId,ratePlanId,checkIn,checkOut)));}
}
