package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.type.MarketplacePublicationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/marketplace-activities")
@RequiredArgsConstructor
public class AdminActivityCommerceController {
    private final ActivityCommerceService service;

    /** Columns this list may be ordered by; anything else falls back to its natural order. */
    private static final java.util.Set<String> PRODUCT_SORT_FIELDS = java.util.Set.of(
            "title", "placeTitle", "priceAmount", "updatedAt", "createdAt");

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceActivityResponse>>> products(
            @RequestParam(required=false) String q, @RequestParam(required=false) String search,
            @RequestParam(required=false) java.util.List<String> status,
            @RequestParam(required=false) java.util.List<UUID> locationImageId,
            @RequestParam(required=false) String sort, @RequestParam(required=false) String direction,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminProducts(q!=null?q:search,
                com.ds.goroute.utils.AdminListSort.codes(status), com.ds.goroute.utils.AdminListSort.ids(locationImageId),
                com.ds.goroute.utils.AdminListSort.field(sort,PRODUCT_SORT_FIELDS),
                com.ds.goroute.utils.AdminListSort.descending(direction),page,size)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','create')")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> create(Authentication authentication,
            @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminCreateProduct(actor(authentication), request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> product(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminProduct(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> update(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateProduct(actor(authentication),id,request)));
    }

    @GetMapping("/{id}/packages")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<ActivityPackageResponse>>> packages(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminPackages(id)));
    }

    @PostMapping("/{id}/packages")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','create')")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> createPackage(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminCreatePackage(actor(authentication),id,request)));
    }

    @PutMapping("/packages/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> updatePackage(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdatePackage(actor(authentication),id,request)));
    }

    @GetMapping("/packages/{id}/slots")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<ActivitySlotResponse>>> slots(@PathVariable UUID id,
            @RequestParam(required=false) LocalDateTime from) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminSlots(id,from)));
    }

    @PostMapping("/packages/{id}/slots")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','create')")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> createSlot(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminCreateSlot(actor(authentication),id,request)));
    }

    @PutMapping("/slots/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> updateSlot(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateSlot(actor(authentication),id,request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> status(Authentication authentication,@PathVariable UUID id,
            @RequestParam MarketplacePublicationStatus status, @RequestParam(required=false) String reason,
            @RequestParam Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminProductStatus(
                actor(authentication),id,status,reason,expectedVersion)));
    }

    /** Columns this list may be ordered by; anything else falls back to its natural order. */
    private static final java.util.Set<String> ORDER_SORT_FIELDS = java.util.Set.of(
            "orderCode", "activityTitle", "totalAmount", "createdAt");

    @GetMapping("/orders")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<ActivityOrderResponse>>> orders(
            @RequestParam(required=false) String q, @RequestParam(required=false) String search,
            @RequestParam(required=false) java.util.List<String> status,
            @RequestParam(required=false) java.util.List<String> paymentStatus,
            @RequestParam(required=false) String sort, @RequestParam(required=false) String direction,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrders(q!=null?q:search,
                com.ds.goroute.utils.AdminListSort.codes(status), com.ds.goroute.utils.AdminListSort.codes(paymentStatus),
                com.ds.goroute.utils.AdminListSort.field(sort,ORDER_SORT_FIELDS),
                com.ds.goroute.utils.AdminListSort.descending(direction),page,size)));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> order(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrder(id)));
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> orderStatus(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody UpdateActivityOrderStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrderStatus(actor(authentication),id,request)));
    }

    private UUID actor(Authentication authentication){return UUID.fromString(authentication.getName());}
}
