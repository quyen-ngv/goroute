package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateActivityPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateAdminLinksPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateManualPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateNationwidePlaceImportJobRequest;
import com.ds.goroute.dto.request.CreatePlaceDetailRefreshJobRequest;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.dto.response.AdminPlaceImportRunResponse;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.service.NationwidePlaceImportJobService;
import com.ds.goroute.service.PlaceDetailRefreshJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/place-import-jobs")
@RequiredArgsConstructor
public class PlaceImportJobController extends BaseController {

    private final PlaceImportJobService placeImportJobService;
    private final NationwidePlaceImportJobService nationwidePlaceImportJobService;
    private final PlaceDetailRefreshJobService placeDetailRefreshJobService;

    @PostMapping("/place-details-refresh")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createPlaceDetailRefresh(
            @Valid @RequestBody CreatePlaceDetailRefreshJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.trigger(request)));
    }

    @GetMapping("/place-details-refresh/{jobId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity getPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.get(jobId)));
    }

    @PostMapping("/place-details-refresh/{jobId}/cancel")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity cancelPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.cancel(jobId)));
    }

    @PostMapping("/place-details-refresh/{jobId}/retry")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity retryPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.retry(jobId)));
    }

    @PostMapping("/nationwide")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createNationwide(@Valid @RequestBody CreateNationwidePlaceImportJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.trigger(request)));
    }

    @PostMapping("/{jobId}/cancel")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity cancelNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.cancel(jobId)));
    }

    @GetMapping("/nationwide/{jobId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity getNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.get(jobId)));
    }

    @PostMapping("/{jobId}/retry")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity retryNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.retry(jobId)));
    }

    @PostMapping("/social-locations")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createFromSocialLocations(
            @Valid @RequestBody CreateSocialPlaceImportJobRequest request) {
        AdminPlaceImportRunResponse response = placeImportJobService.adminRunSocialJobs(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/activities")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createFromActivities(
            @Valid @RequestBody CreateActivityPlaceImportJobRequest request) {
        AdminPlaceImportRunResponse response = placeImportJobService.adminRunActivityJobs(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/manual-link")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createFromManualLink(
            @Valid @RequestBody CreateManualPlaceImportJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeImportJobService.adminRunManualPlaceImport(request)));
    }

    @PostMapping("/links")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity createFromLinks(
            @Valid @RequestBody CreateAdminLinksPlaceImportJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeImportJobService.adminRunLinkImport(request)));
    }

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity listJobs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<PlaceImportJobResponse> response =
                placeImportJobService.adminListJobs(userId, status, sourceType, page, size);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeImportJobService.adminGetJob(jobId)));
    }
}
