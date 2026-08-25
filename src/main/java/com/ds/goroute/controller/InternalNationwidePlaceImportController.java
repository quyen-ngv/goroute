package com.ds.goroute.controller;

import com.ds.goroute.dto.request.NationwideJobEventRequest;
import com.ds.goroute.dto.request.NationwideDuplicateCheckRequest;
import com.ds.goroute.dto.request.NationwidePlaceImportRequest;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.NationwideDuplicateCheckResponse;
import com.ds.goroute.dto.response.NationwidePlaceImportResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.NationwidePlaceImportJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/internal/place-import-jobs/nationwide")
@RequiredArgsConstructor
public class InternalNationwidePlaceImportController extends BaseService {
    private final NationwidePlaceImportJobService service;

    @PostMapping("/events")
    public ResponseEntity<BaseResponse<Void>> event(
            @Valid @RequestBody NationwideJobEventRequest request) {
        service.acceptEvent(request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/imports")
    public ResponseEntity<BaseResponse<NationwidePlaceImportResponse>> importPlace(
            @Valid @RequestBody NationwidePlaceImportRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.importCandidate(request)));
    }

    @PostMapping("/existing-candidates")
    public ResponseEntity<BaseResponse<NationwideDuplicateCheckResponse>> existingCandidates(
            @Valid @RequestBody NationwideDuplicateCheckRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.findExistingCandidates(request)));
    }
}
