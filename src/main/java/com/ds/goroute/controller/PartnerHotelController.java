package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.BookingChangeRequestService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/api/partner/hotels") @RequiredArgsConstructor
public class PartnerHotelController {
    private final HotelMarketplaceService service;
    private final PartnerAuthorizationService authorization;
    private final FileUploadService fileUploadService;
    private final BookingChangeRequestService changeRequests;
    @GetMapping public ResponseEntity<BaseResponse<List<HotelProfileResponse>>> hotels(Authentication a,@RequestParam UUID organizationId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListHotels(user(a),organizationId)));}
    @PostMapping public ResponseEntity<BaseResponse<HotelProfileResponse>> create(Authentication a,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateHotel(user(a),r)));}
    /**
     * One property by id. A deep link — a push notification, a bookmarked workspace tab, the
     * app opening a listing it has never listed — has an id and nothing else; without this it
     * had to fetch every property of the organization and search it client-side.
     */
    @GetMapping("/{hotelId}") public ResponseEntity<BaseResponse<HotelProfileResponse>> hotel(Authentication a,@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetHotel(user(a),hotelId)));}
    @PutMapping("/{hotelId}") public ResponseEntity<BaseResponse<HotelProfileResponse>> update(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertHotelRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateHotel(user(a),hotelId,r)));}
    /**
     * Hotel media goes through the shared upload door (MOD-05). The hand-written type and
     * size checks that used to live here were a copy of the ones in the activity
     * controller and of the ones in the upload service, and none of them checked content.
     */
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<List<ImageUploadOutcome>>> media(
            Authentication a,
            @RequestParam UUID organizationId,
            @RequestParam("files") List<MultipartFile> files) {
        // Property photos (HOTEL_WRITE) and room photos (ROOM_WRITE) share this upload.
        if (!authorization.hasPermission(organizationId, user(a), "HOTEL_WRITE")) {
            authorization.requirePermission(organizationId, user(a), "ROOM_WRITE");
        }
        ImageUploadRequest request = ImageUploadRequest.of(
                user(a),
                ImageUploadRequest.ImageEntryPoint.PARTNER_HOTEL,
                "marketplace/hotels/" + organizationId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(fileUploadService.uploadImages(request, files)));
    }
    @GetMapping("/{hotelId}/promotions") public ResponseEntity<BaseResponse<List<RatePlanPromotionResponse>>> promotions(Authentication a,@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListPromotions(user(a),hotelId)));}
    @PostMapping("/{hotelId}/promotions") public ResponseEntity<BaseResponse<RatePlanPromotionResponse>> createPromotion(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertRatePlanPromotionRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreatePromotion(user(a),hotelId,r)));}
    @PutMapping("/{hotelId}/promotions/{promotionId}") public ResponseEntity<BaseResponse<RatePlanPromotionResponse>> updatePromotion(Authentication a,@PathVariable UUID hotelId,@PathVariable UUID promotionId,@Valid @RequestBody UpsertRatePlanPromotionRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdatePromotion(user(a),hotelId,promotionId,r)));}
    @DeleteMapping("/{hotelId}/promotions/{promotionId}") public ResponseEntity<BaseResponse<Void>> deletePromotion(Authentication a,@PathVariable UUID hotelId,@PathVariable UUID promotionId){service.partnerDeletePromotion(user(a),hotelId,promotionId);return ResponseEntity.ok(BaseResponse.ofSucceeded());}
    @GetMapping("/{hotelId}/readiness") public ResponseEntity<BaseResponse<ListingReadinessResponse>> readiness(Authentication a,@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerHotelReadiness(user(a),hotelId)));}
    @GetMapping("/bookings/{bookingId}/change-requests") public ResponseEntity<BaseResponse<List<BookingChangeRequestResponse>>> changeRequests(Authentication a,@PathVariable UUID bookingId){return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.listForHotelBooking(user(a),bookingId,true)));}
    @PostMapping("/bookings/{bookingId}/change-requests/{requestId}/decide") public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> decideChange(Authentication a,@PathVariable UUID bookingId,@PathVariable UUID requestId,@Valid @RequestBody BookingChangeRequests.Decide decision){return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.decide(user(a),requestId,decision)));}
    @GetMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<List<RoomTypeResponse>>> rooms(Authentication a,@PathVariable UUID hotelId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListRooms(user(a),hotelId)));}
    @PostMapping("/{hotelId}/rooms") public ResponseEntity<BaseResponse<RoomTypeResponse>> createRoom(Authentication a,@PathVariable UUID hotelId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateRoom(user(a),hotelId,r)));}
    @PutMapping("/rooms/{roomId}") public ResponseEntity<BaseResponse<RoomTypeResponse>> updateRoom(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRoomTypeRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateRoom(user(a),roomId,r)));}
    @GetMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<List<RatePlanResponse>>> rates(Authentication a,@PathVariable UUID roomId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListRates(user(a),roomId)));}
    @PostMapping("/rooms/{roomId}/rates") public ResponseEntity<BaseResponse<RatePlanResponse>> createRate(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateRate(user(a),roomId,r)));}
    @PutMapping("/rates/{rateId}") public ResponseEntity<BaseResponse<RatePlanResponse>> updateRate(Authentication a,@PathVariable UUID rateId,@Valid @RequestBody UpsertRatePlanRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateRate(user(a),rateId,r)));}
    @GetMapping("/rooms/{roomId}/inventory") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> inventory(Authentication a,@PathVariable UUID roomId,@RequestParam LocalDate start,@RequestParam LocalDate end){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetInventory(user(a),roomId,start,end)));}
    @PutMapping("/rooms/{roomId}/inventory") public ResponseEntity<BaseResponse<List<RoomInventoryResponse>>> updateInventory(Authentication a,@PathVariable UUID roomId,@Valid @RequestBody BulkUpdateRoomInventoryRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateInventory(user(a),roomId,r)));}
    @GetMapping("/rates/{ratePlanId}/calendar") public ResponseEntity<BaseResponse<List<RatePlanDailyRateResponse>>> rateCalendar(Authentication a,@PathVariable UUID ratePlanId,@RequestParam LocalDate start,@RequestParam LocalDate end){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetRateCalendar(user(a),ratePlanId,start,end)));}
    @PutMapping("/rates/{ratePlanId}/calendar") public ResponseEntity<BaseResponse<List<RatePlanDailyRateResponse>>> updateRateCalendar(Authentication a,@PathVariable UUID ratePlanId,@Valid @RequestBody BulkUpdateRatePlanCalendarRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateRateCalendar(user(a),ratePlanId,r)));}
    @GetMapping("/bookings") public ResponseEntity<BaseResponse<PageResponse<HotelBookingResponse>>> bookings(Authentication a,@RequestParam UUID organizationId,@RequestParam(required=false)String status,@RequestParam(required=false)UUID hotelId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerListBookings(user(a),organizationId,status,hotelId,page,size)));}
    @GetMapping("/bookings/{bookingId}") public ResponseEntity<BaseResponse<HotelBookingResponse>> booking(Authentication a,@PathVariable UUID bookingId){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerGetBooking(user(a),bookingId)));}
    @PatchMapping("/bookings/{bookingId}/status") public ResponseEntity<BaseResponse<HotelBookingResponse>> bookingStatus(Authentication a,@PathVariable UUID bookingId,@Valid @RequestBody UpdateHotelBookingStatusRequest r){return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateBookingStatus(user(a),bookingId,r)));}
    private UUID user(Authentication a){if(a==null||a.getPrincipal()==null)throw new AuthenticationCredentialsNotFoundException("Authentication required");Object p=a.getPrincipal();return p instanceof UUID id?id:UUID.fromString(p.toString());}
}
