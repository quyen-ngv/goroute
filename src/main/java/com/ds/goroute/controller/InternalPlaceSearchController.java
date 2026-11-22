package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;
import com.ds.goroute.service.InternalPlaceSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scrape worker only: authenticated by the scrape callback token in InternalApiAuthenticationFilter. */
@RestController
@RequestMapping("/v1/api/internal/place-search")
@RequiredArgsConstructor
public class InternalPlaceSearchController extends BaseController {

    private final InternalPlaceSearchService internalPlaceSearchService;

    @PostMapping("/text")
    public ResponseEntity<BaseResponse<InternalPlaceSearchResponse>> searchText(
            @Valid @RequestBody InternalPlaceSearchRequest request) {
        return ResponseEntity.ok(ofSucceeded(internalPlaceSearchService.search(request)));
    }
}
