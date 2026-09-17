package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.HotelOfferQuery;
import com.ds.goroute.dto.request.HotelSearchQuery;
import com.ds.goroute.service.HotelOfferService;
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
    private final HotelOfferService offerService;
    /** Every enabled room type with each rate priced for the stay: availability, promotion, taxes share and cancellation terms. */
    @GetMapping("/{hotelId}/offers") public ResponseEntity<BaseResponse<List<HotelRoomOfferResponse>>> offers(@PathVariable UUID hotelId,
            @RequestParam LocalDate checkIn,@RequestParam LocalDate checkOut,@RequestParam(defaultValue="1")int rooms,
            @RequestParam(defaultValue="1")int adults,@RequestParam(defaultValue="0")int children){
        HotelOfferQuery query=HotelOfferQuery.builder().checkIn(checkIn).checkOut(checkOut).rooms(rooms).adults(adults).children(children).build();
        return ResponseEntity.ok(BaseResponse.ofSucceeded(offerService.listOffers(hotelId,query)));}
    /** Availability-aware search: dates + party size only return hotels with a room type that fits and is open every night. */
    @GetMapping public ResponseEntity<BaseResponse<List<HotelProfileResponse>>> list(@RequestParam(required=false)String q,
            @RequestParam(required=false)String propertyType,@RequestParam(required=false)java.math.BigDecimal minPrice,@RequestParam(required=false)java.math.BigDecimal maxPrice,
            @RequestParam(required=false)LocalDate checkIn,@RequestParam(required=false)LocalDate checkOut,
            @RequestParam(required=false)Integer rooms,@RequestParam(required=false)Integer adults,@RequestParam(required=false)Integer children,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        HotelSearchQuery query=HotelSearchQuery.builder().query(q).propertyType(propertyType).minPrice(minPrice).maxPrice(maxPrice).checkIn(checkIn).checkOut(checkOut).rooms(rooms).adults(adults).children(children).build();
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublic(query,page,size)));}
    @GetMapping("/{hotelId}") public ResponseEntity<BaseResponse<HotelProfileResponse>> get(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getPublic(hotelId)));}
    @GetMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<List<RoomTypeResponse>>> rooms(@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublicRooms(hotelId)));}
    @GetMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<List<RatePlanResponse>>> rates(@PathVariable UUID roomId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listPublicRates(roomId)));}
    @GetMapping("/{hotelId}/availability") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> availability(@PathVariable UUID hotelId,@RequestParam UUID roomTypeId,@RequestParam UUID ratePlanId,@RequestParam LocalDate checkIn,@RequestParam LocalDate checkOut,@RequestParam(defaultValue="1")Integer quantity,@RequestParam(defaultValue="1")Integer adults,@RequestParam(defaultValue="0")Integer children){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getAvailability(hotelId,roomTypeId,ratePlanId,checkIn,checkOut,quantity,adults,children)));}
}
