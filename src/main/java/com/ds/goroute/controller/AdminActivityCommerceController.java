package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.ActivityCommerceService;
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

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceActivityResponse>>> products(
            @RequestParam(required=false) String q, @RequestParam(required=false) String search,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminProducts(q!=null?q:search,status,page,size)));
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
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> status(@PathVariable UUID id,
            @RequestParam String status, @RequestParam(required=false) String reason) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminProductStatus(id,status,reason)));
    }

    @GetMapping("/orders")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<List<ActivityOrderResponse>>> orders(
            @RequestParam(required=false) String q, @RequestParam(required=false) String search,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrders(q!=null?q:search,status,page,size)));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','get')")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> order(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrder(id)));
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-activities','update')")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> orderStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateActivityOrderStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminOrderStatus(id,request)));
    }

    private UUID actor(Authentication authentication){return UUID.fromString(authentication.getName());}
}
