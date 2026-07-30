package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateHotelBookingRequest;
import com.ds.goroute.dto.response.HotelBookingResponse;
import com.ds.goroute.service.HotelMarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/api/hotel-bookings") @RequiredArgsConstructor
public class HotelBookingController {
    private final HotelMarketplaceService service;
    @PostMapping public ResponseEntity<BaseResponse<HotelBookingResponse>> create(Authentication a,@Valid @RequestBody CreateHotelBookingRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.createBooking(user(a),r)));}
    @GetMapping public ResponseEntity<BaseResponse<List<HotelBookingResponse>>> list(Authentication a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMyBookings(user(a),page,size)));}
    @GetMapping("/{id}") public ResponseEntity<BaseResponse<HotelBookingResponse>> get(Authentication a,@PathVariable UUID id){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMyBooking(user(a),id)));}
    @PostMapping("/{id}/cancel") public ResponseEntity<BaseResponse<HotelBookingResponse>> cancel(Authentication a,@PathVariable UUID id,@RequestParam(required=false)String reason,@RequestParam(required=false)Long expectedVersion){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.cancelMyBooking(user(a),id,reason,expectedVersion)));}
    private UUID user(Authentication a){if(a==null||a.getPrincipal()==null)throw new AuthenticationCredentialsNotFoundException("Authentication required");Object p=a.getPrincipal();return p instanceof UUID id?id:UUID.fromString(p.toString());}
}
