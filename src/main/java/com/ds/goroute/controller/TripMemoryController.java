package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.request.UpdateTripMemoryRequest;
import com.ds.goroute.dto.response.TripMemoryResponse;
import com.ds.goroute.service.TripMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}/memories")
@RequiredArgsConstructor
public class TripMemoryController extends BaseController {
    private final TripMemoryService tripMemoryService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<TripMemoryResponse>>> getMemories(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID activityId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                tripMemoryService.getTripMemories(tripId, userId, activityId)
        ));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<TripMemoryResponse>> addMemory(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTripMemoryRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(tripMemoryService.addTripMemory(tripId, request, userId)));
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<TripMemoryResponse>> addVideoMemory(
            @PathVariable UUID tripId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID activityId,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(tripMemoryService.addTripVideoMemory(tripId, activityId, file, userId)));
    }

    /**
     * Edits the title and description of a memory. The uploader only; the image
     * url is not editable here.
     */
    @PatchMapping("/{memoryId}")
    public ResponseEntity<BaseResponse<TripMemoryResponse>> updateMemory(
            @PathVariable UUID tripId,
            @PathVariable UUID memoryId,
            @Valid @RequestBody UpdateTripMemoryRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                tripMemoryService.updateTripMemory(tripId, memoryId, request, userId)
        ));
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<BaseResponse<Void>> deleteMemory(
            @PathVariable UUID tripId,
            @PathVariable UUID memoryId,
            @CurrentUser UUID userId) {
        tripMemoryService.deleteTripMemory(tripId, memoryId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
