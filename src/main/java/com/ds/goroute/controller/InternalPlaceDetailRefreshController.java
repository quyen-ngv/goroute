package com.ds.goroute.controller;

import com.ds.goroute.dto.request.PlaceDetailRefreshJobEventRequest;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.PlaceDetailRefreshJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/internal/place-import-jobs/place-details-refresh")
@RequiredArgsConstructor
public class InternalPlaceDetailRefreshController extends BaseController {
    private final PlaceDetailRefreshJobService service;

    @PostMapping("/events")
    public ResponseEntity<BaseResponse<Void>> event(
            @Valid @RequestBody PlaceDetailRefreshJobEventRequest request) {
        service.acceptEvent(request);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
