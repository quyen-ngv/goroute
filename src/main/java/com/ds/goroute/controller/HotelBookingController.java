package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateHotelBookingRequest;
import com.ds.goroute.dto.request.BookingChangeRequests;
import com.ds.goroute.dto.response.BookingChangeRequestResponse;
import com.ds.goroute.dto.response.CancellationPreviewResponse;
import com.ds.goroute.service.BookingChangeRequestService;
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
    private final BookingChangeRequestService changeRequests;
    @PostMapping public ResponseEntity<BaseResponse<HotelBookingResponse>> create(Authentication a,@RequestHeader(value="Idempotency-Key",required=false)String idempotencyKey,@Valid @RequestBody CreateHotelBookingRequest r){if((r.getIdempotencyKey()==null||r.getIdempotencyKey().isBlank())&&idempotencyKey!=null)r.setIdempotencyKey(idempotencyKey);return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.createBooking(user(a),r)));}
    @GetMapping public ResponseEntity<BaseResponse<List<HotelBookingResponse>>> list(Authentication a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMyBookings(user(a),page,size)));}
    @GetMapping("/{id}") public ResponseEntity<BaseResponse<HotelBookingResponse>> get(Authentication a,@PathVariable UUID id){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMyBooking(user(a),id)));}
    @GetMapping("/{id}/cancellation-preview") public ResponseEntity<BaseResponse<CancellationPreviewResponse>> cancellationPreview(Authentication a,@PathVariable UUID id){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.previewMyCancellation(user(a),id)));}
    @PostMapping("/{id}/change-requests") public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> requestChange(Authentication a,@PathVariable UUID id,@Valid @RequestBody BookingChangeRequests.CreateHotelChange r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(changeRequests.requestHotelChange(user(a),id,r)));}
    @GetMapping("/{id}/change-requests") public ResponseEntity<BaseResponse<List<BookingChangeRequestResponse>>> changeRequests(Authentication a,@PathVariable UUID id){return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.listForHotelBooking(user(a),id,false)));}
    @PostMapping("/{id}/change-requests/{requestId}/withdraw") public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> withdrawChange(Authentication a,@PathVariable UUID id,@PathVariable UUID requestId){return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.withdraw(user(a),requestId)));}
    @PostMapping("/{id}/cancel") public ResponseEntity<BaseResponse<HotelBookingResponse>> cancel(Authentication a,@PathVariable UUID id,@RequestParam(required=false)String reason,@RequestParam(required=false)Long expectedVersion){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.cancelMyBooking(user(a),id,reason,expectedVersion)));}
    private UUID user(Authentication a){if(a==null||a.getPrincipal()==null)throw new AuthenticationCredentialsNotFoundException("Authentication required");Object p=a.getPrincipal();return p instanceof UUID id?id:UUID.fromString(p.toString());}
}
