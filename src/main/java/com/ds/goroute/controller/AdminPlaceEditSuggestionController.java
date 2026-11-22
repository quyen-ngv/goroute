package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ReviewPlaceEditSuggestionRequest;
import com.ds.goroute.dto.response.AdminPlaceEditDetailResponse;
import com.ds.goroute.dto.response.AdminPlaceEditListItem;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PlaceEditCountResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.place.PlaceEditReviewService;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Console review of place edit suggestions (epic 09, plan §3.10). ROLE_ADMIN by the
 * /v1/api/admin/** rule plus {@code place-edits:get|update}; unlocking a field is {@code places:update}.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class AdminPlaceEditSuggestionController {

    private final PlaceEditReviewService reviewService;

    /** PENDING is ordered oldest first, any other status newest first. */
    @GetMapping("/v1/api/admin/place-edit-suggestions")
    @PreAuthorize("@adminAuthorization.can(authentication,'place-edits','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminPlaceEditListItem>>> list(
            @RequestParam(required = false) PlaceEditStatus status,
            @RequestParam(required = false) PlaceEditType type,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                reviewService.list(status, type, placeId, userId, page, size)));
    }

    @GetMapping("/v1/api/admin/place-edit-suggestions/count")
    @PreAuthorize("@adminAuthorization.can(authentication,'place-edits','get')")
    public ResponseEntity<BaseResponse<PlaceEditCountResponse>> count(
            @RequestParam(defaultValue = "PENDING") PlaceEditStatus status) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(reviewService.count(status)));
    }

    @GetMapping("/v1/api/admin/place-edit-suggestions/{suggestionId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'place-edits','get')")
    public ResponseEntity<BaseResponse<AdminPlaceEditDetailResponse>> detail(@PathVariable UUID suggestionId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(reviewService.detail(suggestionId)));
    }

    @PostMapping("/v1/api/admin/place-edit-suggestions/{suggestionId}/review")
    @PreAuthorize("@adminAuthorization.can(authentication,'place-edits','update')")
    public ResponseEntity<BaseResponse<AdminPlaceEditDetailResponse>> review(
            @CurrentUser UUID reviewerId,
            @PathVariable UUID suggestionId,
            @Valid @RequestBody ReviewPlaceEditSuggestionRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                reviewService.review(suggestionId, request, reviewerId, PlaceEditReviewChannel.CONSOLE)));
    }

    /** Lets the re-scrape write the field again; returns the fields still locked. */
    @DeleteMapping("/v1/api/admin/places/{placeId}/locked-fields/{field}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<List<String>>> unlockField(@PathVariable UUID placeId,
                                                                  @PathVariable String field) {
        PlaceEditField parsed = PlaceEditField.fromName(field);
        if (parsed == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Unknown field " + field);
        }
        return ResponseEntity.ok(BaseResponse.ofSucceeded(reviewService.unlockField(placeId, parsed)));
    }
}
