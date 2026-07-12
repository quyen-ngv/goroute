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
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String itemType,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = currentUserId(authentication);
        List<SavedPlaceResponse> places = savedPlaceService.getSavedPlaces(userId, category, itemType, page, size);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(places));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<SavedPlaceResponse>> savePlace(
            Authentication authentication,
            @Valid @RequestBody SavePlaceRequest request) {
        UUID userId = currentUserId(authentication);
        SavedPlaceResponse saved = savedPlaceService.savePlace(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(saved));
    }

    @DeleteMapping("/{savedPlaceId}")
    public ResponseEntity<BaseResponse<Void>> unsavePlace(
            Authentication authentication,
            @PathVariable UUID savedPlaceId) {
        UUID userId = currentUserId(authentication);
        savedPlaceService.unsavePlace(userId, savedPlaceId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PutMapping("/{savedPlaceId}/tags")
    public ResponseEntity<BaseResponse<SavedPlaceResponse>> updateTags(
            Authentication authentication,
            @PathVariable UUID savedPlaceId,
            @RequestBody List<String> tags) {
        UUID userId = currentUserId(authentication);
        SavedPlaceResponse updated = savedPlaceService.updateTags(userId, savedPlaceId, tags);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(updated));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(principal.toString());
    }
}
