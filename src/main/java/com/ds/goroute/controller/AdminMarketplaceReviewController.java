package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceReviewViewResponse;
import com.ds.goroute.dto.request.UpsertMarketplaceReviewResponseRequest;
import com.ds.goroute.service.MarketplaceReviewResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/marketplace-reviews")
@RequiredArgsConstructor
public class AdminMarketplaceReviewController {
    private final MarketplaceReviewResponseService service;

    /** Columns this list may be ordered by; anything else falls back to its natural order. */
    private static final java.util.Set<String> REVIEW_SORT_FIELDS = java.util.Set.of(
            "subjectName", "reviewerName", "overallRating", "responseUpdatedAt", "createdAt");

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-reviews','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceReviewViewResponse>>> list(
            @RequestParam(required=false) String q,@RequestParam(required=false) String search,
            @RequestParam(required=false) java.util.List<String> responseStatus,
            @RequestParam(required=false) String sort,@RequestParam(required=false) String direction,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminList(q!=null?q:search,com.ds.goroute.utils.AdminListSort.codes(responseStatus),
                com.ds.goroute.utils.AdminListSort.field(sort,REVIEW_SORT_FIELDS),
                com.ds.goroute.utils.AdminListSort.descending(direction),page,size)));
    }

    @PutMapping("/{reviewId}/response")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-reviews','update')")
    public ResponseEntity<BaseResponse<MarketplaceReviewViewResponse>> respond(Authentication authentication,
            @PathVariable UUID reviewId,@RequestParam UUID organizationId,
            @Valid @RequestBody UpsertMarketplaceReviewResponseRequest request){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminRespond(UUID.fromString(authentication.getName()),organizationId,reviewId,request)));
    }

    @DeleteMapping("/{reviewId}/response")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-reviews','delete')")
    public ResponseEntity<BaseResponse<MarketplaceReviewViewResponse>> hide(Authentication authentication,
            @PathVariable UUID reviewId,@RequestParam UUID organizationId,@RequestParam(required=false)String reason,
            @RequestParam Long expectedVersion){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminHide(
                UUID.fromString(authentication.getName()),organizationId,reviewId,reason,expectedVersion)));
    }
}
