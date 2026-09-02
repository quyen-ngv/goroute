package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BookingChangeRequestService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/partner/activities")
@RequiredArgsConstructor
public class PartnerActivityCommerceController {

    private final ActivityCommerceService service;
    private final BookingChangeRequestService changeRequests;
    private final PartnerAuthorizationService authorization;
    private final FileUploadService fileUploadService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<MarketplaceActivityResponse>>> list(Authentication authentication,
                                                                                  @RequestParam UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerList(user(authentication), organizationId)));
    }

    /**
     * Receives media only after the host confirms the create/edit action. The browser keeps selected files locally until then.
     */
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<List<ImageUploadOutcome>>> uploadMedia(
            Authentication authentication,
            @RequestParam UUID organizationId,
            @RequestParam("files") List<MultipartFile> files) {
        UUID actor = user(authentication);
        authorization.requirePermission(organizationId, actor, "ACTIVITY_WRITE");

        // Size, type and magic-byte checks used to be written out again here; they now
        // live once, in the shared upload door, together with content moderation (MOD-05).
        ImageUploadRequest request = ImageUploadRequest.of(
                actor,
                ImageUploadRequest.ImageEntryPoint.PARTNER_ACTIVITY,
                "marketplace/activities/" + organizationId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(fileUploadService.uploadImages(request, files)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> create(Authentication authentication,
                                                                              @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreate(user(authentication), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> update(Authentication authentication,
                                                                              @PathVariable UUID id,
                                                                              @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdate(user(authentication), id, request)));
    }

    @GetMapping("/{id}/packages")
    public ResponseEntity<BaseResponse<List<ActivityPackageResponse>>> packages(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerPackages(user(authentication), id)));
    }

    @PostMapping("/{id}/packages")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> createPackage(Authentication authentication, @PathVariable UUID id,
                                                                                @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreatePackage(user(authentication), id, request)));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> updatePackage(Authentication authentication, @PathVariable UUID id,
                                                                                @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdatePackage(user(authentication), id, request)));
    }

    @GetMapping("/packages/{id}/slots")
    public ResponseEntity<BaseResponse<List<ActivitySlotResponse>>> slots(Authentication authentication, @PathVariable UUID id,
                                                                           @RequestParam(required = false) LocalDateTime from) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerSlots(user(authentication), id, from)));
    }

    @PostMapping("/packages/{id}/slots")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> createSlot(Authentication authentication, @PathVariable UUID id,
                                                                          @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateSlot(user(authentication), id, request)));
    }

    @PostMapping("/packages/{id}/slots/bulk")
    public ResponseEntity<BaseResponse<List<ActivitySlotResponse>>> createSlots(Authentication authentication, @PathVariable UUID id,
                                                                                 @Valid @RequestBody BulkCreateActivitySlotsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateSlots(user(authentication), id, request)));
    }

    @PutMapping("/slots/{id}")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> updateSlot(Authentication authentication, @PathVariable UUID id,
                                                                          @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateSlot(user(authentication), id, request)));
    }

    @GetMapping("/orders")
    public ResponseEntity<BaseResponse<PageResponse<ActivityOrderResponse>>> orders(Authentication authentication, @RequestParam UUID organizationId,
                                                                            @RequestParam(required = false) String status,
                                                                            @RequestParam(required = false) UUID activityId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrders(user(authentication), organizationId, status, activityId, page, size)));
    }

    /** Ticket scanner: redeem by voucher code (CONFIRMED -> CHECKED_IN, redemption stamped). */
    @GetMapping("/{id}/readiness")
    public ResponseEntity<BaseResponse<ListingReadinessResponse>> readiness(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerProductReadiness(user(authentication), id)));
    }

    @GetMapping("/orders/{id}/change-requests")
    public ResponseEntity<BaseResponse<List<BookingChangeRequestResponse>>> changeRequests(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.listForActivityOrder(user(authentication), id, true)));
    }

    @PostMapping("/orders/{id}/change-requests/{requestId}/decide")
    public ResponseEntity<BaseResponse<BookingChangeRequestResponse>> decideChange(Authentication authentication, @PathVariable UUID id, @PathVariable UUID requestId, @Valid @RequestBody BookingChangeRequests.Decide decision) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(changeRequests.decide(user(authentication), requestId, decision)));
    }

    @PostMapping("/orders/redeem")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> redeemByVoucher(Authentication authentication, @Valid @RequestBody RedeemVoucherRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerRedeemByVoucher(user(authentication), request.getOrganizationId(), request.getVoucherCode())));
    }

    @PostMapping("/orders/{id}/redeem")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> redeem(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerRedeem(user(authentication), id)));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> order(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrder(user(authentication), id)));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> status(Authentication authentication, @PathVariable UUID id,
                                                                       @Valid @RequestBody UpdateActivityOrderStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrderStatus(user(authentication), id, request)));
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID id ? id : UUID.fromString(principal.toString());
    }
}
