package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.service.SavedPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/saved-places")
@RequiredArgsConstructor
@Slf4j
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<SavedPlaceResponse>>> getSavedPlaces(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<SavedPlaceResponse> places = savedPlaceService.getSavedPlaces(userId, category, page, size);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(places));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<SavedPlaceResponse>> savePlace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SavePlaceRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        SavedPlaceResponse saved = savedPlaceService.savePlace(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(saved));
    }

    @DeleteMapping("/{savedPlaceId}")
    public ResponseEntity<BaseResponse<Void>> unsavePlace(@PathVariable UUID savedPlaceId) {
        savedPlaceService.unsavePlace(savedPlaceId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PutMapping("/{savedPlaceId}/tags")
    public ResponseEntity<BaseResponse<SavedPlaceResponse>> updateTags(
            @PathVariable UUID savedPlaceId,
            @RequestBody List<String> tags) {
        SavedPlaceResponse updated = savedPlaceService.updateTags(savedPlaceId, tags);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(updated));
    }
}
