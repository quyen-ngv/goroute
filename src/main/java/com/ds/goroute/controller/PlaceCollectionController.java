package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AddCollectionItemRequest;
import com.ds.goroute.dto.request.UpsertPlaceCollectionRequest;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PlaceCollectionResponse;
import com.ds.goroute.service.PlaceCollectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Shareable collections of places (SOC-04). */
@RestController
@RequestMapping("/v1/api/collections")
@RequiredArgsConstructor
@Validated
public class PlaceCollectionController extends BaseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PlaceCollectionService service;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<PlaceCollectionResponse>>> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser UUID userId) {
        List<PlaceCollectionResponse> items = service.listMine(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, service.countMine(userId), page, size)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> create(
            @Valid @RequestBody UpsertPlaceCollectionRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(service.create(userId, request)));
    }

    @GetMapping("/{collectionId}")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> get(
            @PathVariable UUID collectionId,
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.get(userId, collectionId)));
    }

    @PutMapping("/{collectionId}")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> update(
            @PathVariable UUID collectionId,
            @Valid @RequestBody UpsertPlaceCollectionRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.update(userId, collectionId, request)));
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID collectionId,
            @CurrentUser UUID userId) {
        service.delete(userId, collectionId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{collectionId}/items")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> addItem(
            @PathVariable UUID collectionId,
            @Valid @RequestBody AddCollectionItemRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(service.addItem(userId, collectionId, request)));
    }

    @DeleteMapping("/{collectionId}/items/{itemId}")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> removeItem(
            @PathVariable UUID collectionId,
            @PathVariable UUID itemId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.removeItem(userId, collectionId, itemId)));
    }

    @PutMapping("/{collectionId}/order")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> reorder(
            @PathVariable UUID collectionId,
            @RequestBody List<UUID> orderedItemIds,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.reorder(userId, collectionId, orderedItemIds)));
    }

    /** Public read by share link. Returns only this collection, never the saved list. */
    @GetMapping("/shared/{shareSlug}")
    public ResponseEntity<BaseResponse<PlaceCollectionResponse>> shared(
            @PathVariable @Size(max = 40) String shareSlug) {
        return ResponseEntity.ok(ofSucceeded(service.getByShareSlug(shareSlug)));
    }
}
