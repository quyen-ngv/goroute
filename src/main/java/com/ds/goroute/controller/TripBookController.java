package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.PatchBookPageSlotRequest;
import com.ds.goroute.dto.request.UpdateBookPageRequest;
import com.ds.goroute.dto.request.UpsertBookSkeletonRequest;
import com.ds.goroute.dto.response.BookPageResponse;
import com.ds.goroute.dto.response.BookSkeletonResponse;
import com.ds.goroute.dto.response.BookSlotResponse;
import com.ds.goroute.dto.response.TripBookResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.TripBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class TripBookController extends BaseService {
    private final TripBookService tripBookService;

    @PostMapping("/trips/{tripId}/book/generate")
    public ResponseEntity<BaseResponse<TripBookResponse>> generateBook(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.generateBook(tripId, userId)));
    }

    @GetMapping("/trips/{tripId}/book")
    public ResponseEntity<BaseResponse<TripBookResponse>> getBook(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.getBook(tripId, userId)));
    }

    @GetMapping("/book/skeletons")
    public ResponseEntity<BaseResponse<List<BookSkeletonResponse>>> getSkeletons() {
        return ResponseEntity.ok(ofSucceeded(tripBookService.getSkeletons()));
    }

    @PostMapping("/admin/book/skeletons")
    public ResponseEntity<BaseResponse<BookSkeletonResponse>> upsertSkeleton(
            @Valid @RequestBody UpsertBookSkeletonRequest request) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.upsertSkeleton(request)));
    }

    @PutMapping("/book/pages/{pageId}")
    public ResponseEntity<BaseResponse<BookPageResponse>> updatePage(
            @PathVariable UUID pageId,
            @Valid @RequestBody UpdateBookPageRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.updatePage(pageId, request, userId)));
    }

    @PatchMapping("/book/pages/{pageId}/slots/{slotId}")
    public ResponseEntity<BaseResponse<BookSlotResponse>> patchPageSlot(
            @PathVariable UUID pageId,
            @PathVariable String slotId,
            @RequestBody PatchBookPageSlotRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.patchPageSlot(pageId, slotId, request, userId)));
    }

    @PostMapping("/book/pages/{pageId}/reset-layout")
    public ResponseEntity<BaseResponse<BookPageResponse>> resetPageLayout(
            @PathVariable UUID pageId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripBookService.resetPageLayout(pageId, userId)));
    }
}
