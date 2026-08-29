package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateGuideBookingRequest;
import com.ds.goroute.dto.request.UpsertGuideProfileRequest;
import com.ds.goroute.dto.request.UpsertGuideServiceRequest;
import com.ds.goroute.dto.response.GuideBookingResponse;
import com.ds.goroute.dto.response.GuideProfileResponse;
import com.ds.goroute.dto.response.GuideServiceResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.GuideAvailability;
import com.ds.goroute.entity.GuidePayoutEntry;
import com.ds.goroute.entity.GuideReview;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.GuideBookingService;
import com.ds.goroute.service.GuideDirectoryService;
import com.ds.goroute.type.GuideBookingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VietdeGuide (epic 07).
 *
 * <p>Three audiences on one path prefix: anyone browsing the directory, the guide managing
 * their own profile under {@code /me}, and the traveller managing their bookings. Only the
 * directory reads are public.
 */
@RestController
@RequestMapping("/v1/api/guides")
@RequiredArgsConstructor
@Validated
public class GuideController extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final GuideDirectoryService directoryService;
    private final GuideBookingService bookingService;

    // --- public directory -------------------------------------------------------------

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<PageResponse<GuideServiceResponse>>> search(
            @RequestParam(required = false) @Size(max = 10) String provinceCode,
            @RequestParam(required = false) @Size(max = 40) String language,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<GuideServiceResponse> items = directoryService.search(
                provinceCode, language, maxPrice, date, page, size);
        long total = directoryService.countSearch(provinceCode, language, maxPrice, date);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    @GetMapping("/{guideId}")
    public ResponseEntity<BaseResponse<GuideProfileResponse>> publicProfile(@PathVariable UUID guideId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.publicProfile(guideId)));
    }

    @GetMapping("/{guideId}/services")
    public ResponseEntity<BaseResponse<List<GuideServiceResponse>>> publicServices(
            @PathVariable UUID guideId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.publicServices(guideId)));
    }

    @GetMapping("/services/{serviceId}")
    public ResponseEntity<BaseResponse<GuideServiceResponse>> publicService(@PathVariable UUID serviceId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.publicService(serviceId)));
    }

    @GetMapping("/{guideId}/reviews")
    public ResponseEntity<BaseResponse<List<GuideReview>>> reviews(
            @PathVariable UUID guideId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(bookingService.reviewsForGuide(guideId, page, size)));
    }

    // --- the guide's own workspace ----------------------------------------------------

    @PutMapping("/me")
    public ResponseEntity<BaseResponse<GuideProfileResponse>> upsertProfile(
            @Valid @RequestBody UpsertGuideProfileRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.createOrUpdateProfile(userId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<GuideProfileResponse>> myProfile(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.myProfile(userId)));
    }

    @PostMapping("/me/submit")
    public ResponseEntity<BaseResponse<GuideProfileResponse>> submit(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.submitForVerification(userId)));
    }

    @PostMapping("/me/documents")
    public ResponseEntity<BaseResponse<Void>> attachDocument(
            @RequestParam @Size(max = 40) String documentType,
            @RequestParam @Size(max = 1000) String fileUrl,
            @RequestAttribute("userId") UUID userId) {
        directoryService.attachIdentityDocument(userId, documentType, fileUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(null));
    }

    @GetMapping("/me/services")
    public ResponseEntity<BaseResponse<List<GuideServiceResponse>>> myServices(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.myServices(userId)));
    }

    @PostMapping("/me/services")
    public ResponseEntity<BaseResponse<GuideServiceResponse>> createService(
            @Valid @RequestBody UpsertGuideServiceRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(directoryService.createService(userId, request)));
    }

    @PutMapping("/me/services/{serviceId}")
    public ResponseEntity<BaseResponse<GuideServiceResponse>> updateService(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpsertGuideServiceRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.updateService(userId, serviceId, request)));
    }

    @GetMapping("/me/availability")
    public ResponseEntity<BaseResponse<List<GuideAvailability>>> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.availability(userId, from, to)));
    }

    @PutMapping("/me/availability")
    public ResponseEntity<BaseResponse<Void>> setAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean blocked,
            @RequestParam(required = false) @Min(1) Integer maxGuests,
            @RequestParam(required = false) @Size(max = 300) String note,
            @RequestAttribute("userId") UUID userId) {
        directoryService.setAvailability(userId, date, blocked, maxGuests, note);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/me/bookings")
    public ResponseEntity<BaseResponse<List<GuideBookingResponse>>> guideBookings(
            @RequestParam(required = false) GuideBookingStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.forGuide(userId, status, page, size)));
    }

    @PostMapping("/me/bookings/{bookingId}/respond")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> respond(
            @PathVariable UUID bookingId,
            @RequestParam boolean accept,
            @RequestParam(required = false) @Size(max = 1000) String reason,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.respond(userId, bookingId, accept, reason)));
    }

    @PostMapping("/me/bookings/{bookingId}/complete")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> complete(
            @PathVariable UUID bookingId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.complete(userId, bookingId)));
    }

    @GetMapping("/me/performance")
    public ResponseEntity<BaseResponse<Map<String, Object>>> performance(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.performance(userId, days)));
    }

    @GetMapping("/me/payouts")
    public ResponseEntity<BaseResponse<List<GuidePayoutEntry>>> payouts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.payoutLedger(userId, page, size)));
    }

    @PostMapping("/me/reviews/{reviewId}/respond")
    public ResponseEntity<BaseResponse<GuideReview>> respondToReview(
            @PathVariable UUID reviewId,
            @RequestParam @Size(max = 2000) String response,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.respondToReview(userId, reviewId, response)));
    }

    // --- the traveller's side ----------------------------------------------------------

    @PostMapping("/bookings")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> book(
            @Valid @RequestBody CreateGuideBookingRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(bookingService.request(userId, request)));
    }

    @GetMapping("/bookings")
    public ResponseEntity<BaseResponse<List<GuideBookingResponse>>> myBookings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.forTraveler(userId, page, size)));
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> booking(
            @PathVariable UUID bookingId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.get(userId, bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/confirm")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> confirm(
            @PathVariable UUID bookingId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.confirm(userId, bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> cancel(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) @Size(max = 1000) String reason,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.cancel(userId, bookingId, reason)));
    }

    @PostMapping("/bookings/{bookingId}/dispute")
    public ResponseEntity<BaseResponse<GuideBookingResponse>> dispute(
            @PathVariable UUID bookingId,
            @RequestParam @Size(max = 1000) String reason,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(bookingService.openDispute(userId, bookingId, reason)));
    }

    @PostMapping("/bookings/{bookingId}/review")
    public ResponseEntity<BaseResponse<GuideReview>> review(
            @PathVariable UUID bookingId,
            @RequestParam @Min(1) @Max(5) int rating,
            @RequestParam(required = false) @Size(max = 3000) String comment,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(bookingService.review(userId, bookingId, rating, comment)));
    }
}
