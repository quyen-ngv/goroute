package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.response.TripMemoryResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.TripMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}/memories")
@RequiredArgsConstructor
public class TripMemoryController extends BaseService {
    private final TripMemoryService tripMemoryService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<TripMemoryResponse>>> getMemories(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID activityId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                tripMemoryService.getTripMemories(tripId, userId, activityId)
        ));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<TripMemoryResponse>> addMemory(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTripMemoryRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(tripMemoryService.addTripMemory(tripId, request, userId)));
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<BaseResponse<Void>> deleteMemory(
            @PathVariable UUID tripId,
            @PathVariable UUID memoryId,
            @RequestAttribute("userId") UUID userId) {
        tripMemoryService.deleteTripMemory(tripId, memoryId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
