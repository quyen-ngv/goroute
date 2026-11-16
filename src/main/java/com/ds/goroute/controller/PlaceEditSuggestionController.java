package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreatePlaceEditSuggestionRequest;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PlaceEditContextResponse;
import com.ds.goroute.dto.response.PlaceEditSuggestionResponse;
import com.ds.goroute.service.place.PlaceEditSuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Place edit suggestions from the app (epic 09, plan §3.10). Signed-in users only: SecurityConfig
 * lists /places/{id}/edit-suggestions before the public GET /places/** rule.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class PlaceEditSuggestionController {

    private final PlaceEditSuggestionService service;

    @GetMapping("/v1/api/places/{placeId}/edit-suggestions/context")
    public ResponseEntity<BaseResponse<PlaceEditContextResponse>> context(@CurrentUser UUID userId,
                                                                          @PathVariable UUID placeId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.context(userId, placeId)));
    }

    @PostMapping("/v1/api/places/{placeId}/edit-suggestions")
    public ResponseEntity<BaseResponse<PlaceEditSuggestionResponse>> submit(
            @CurrentUser UUID userId,
            @PathVariable UUID placeId,
            @Valid @RequestBody CreatePlaceEditSuggestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(service.submit(userId, placeId, request)));
    }

    @GetMapping("/v1/api/place-edit-suggestions/me")
    public ResponseEntity<BaseResponse<PageResponse<PlaceEditSuggestionResponse>>> mine(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.mine(userId, page, size)));
    }

    @DeleteMapping("/v1/api/place-edit-suggestions/{suggestionId}")
    public ResponseEntity<BaseResponse<Void>> cancel(@CurrentUser UUID userId, @PathVariable UUID suggestionId) {
        service.cancel(userId, suggestionId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
