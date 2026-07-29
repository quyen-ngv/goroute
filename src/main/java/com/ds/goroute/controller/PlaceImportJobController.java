package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateActivityPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateManualPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateNationwidePlaceImportJobRequest;
import com.ds.goroute.dto.request.CreatePlaceDetailRefreshJobRequest;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.dto.response.AdminPlaceImportRunResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.service.NationwidePlaceImportJobService;
import com.ds.goroute.service.PlaceDetailRefreshJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/place-import-jobs")
@RequiredArgsConstructor
@Tag(name = "Admin Place Import Jobs", description = "Admin-only async jobs that import inactive user-discovered places")
public class PlaceImportJobController extends BaseService {

    private final PlaceImportJobService placeImportJobService;
    private final NationwidePlaceImportJobService nationwidePlaceImportJobService;
    private final PlaceDetailRefreshJobService placeDetailRefreshJobService;

    @PostMapping("/place-details-refresh")
    @Operation(summary = "Refresh Google Maps detail fields for existing places")
    public ResponseEntity createPlaceDetailRefresh(
            @Valid @RequestBody CreatePlaceDetailRefreshJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.trigger(request)));
    }

    @GetMapping("/place-details-refresh/{jobId}")
    @Operation(summary = "Get a place detail refresh job")
    public ResponseEntity getPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.get(jobId)));
    }

    @PostMapping("/place-details-refresh/{jobId}/cancel")
    @Operation(summary = "Cancel a place detail refresh job")
    public ResponseEntity cancelPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.cancel(jobId)));
    }

    @PostMapping("/place-details-refresh/{jobId}/retry")
    @Operation(summary = "Retry a failed or cancelled place detail refresh job")
    public ResponseEntity retryPlaceDetailRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeDetailRefreshJobService.retry(jobId)));
    }

    @PostMapping("/nationwide")
    @Operation(summary = "Start the nationwide legacy-64-region food place import")
    public ResponseEntity createNationwide(@Valid @RequestBody CreateNationwidePlaceImportJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.trigger(request)));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a nationwide place import job")
    public ResponseEntity cancelNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.cancel(jobId)));
    }

    @GetMapping("/nationwide/{jobId}")
    @Operation(summary = "Get nationwide job details including region progress")
    public ResponseEntity getNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.get(jobId)));
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "Retry a failed or cancelled nationwide place import job")
    public ResponseEntity retryNationwide(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(nationwidePlaceImportJobService.retry(jobId)));
    }

    @PostMapping("/social-locations")
    @Operation(summary = "Queue social place imports for the requested user")
    public ResponseEntity createFromSocialLocations(
            @Valid @RequestBody CreateSocialPlaceImportJobRequest request) {
        AdminPlaceImportRunResponse response = placeImportJobService.adminRunSocialJobs(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/activities")
    @Operation(summary = "Queue activity place imports for the requested user")
    public ResponseEntity createFromActivities(
            @Valid @RequestBody CreateActivityPlaceImportJobRequest request) {
        AdminPlaceImportRunResponse response = placeImportJobService.adminRunActivityJobs(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/manual-link")
    @Operation(summary = "Queue an import for an admin-provided Google Maps place link")
    public ResponseEntity createFromManualLink(
            @Valid @RequestBody CreateManualPlaceImportJobRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeImportJobService.adminRunManualPlaceImport(request)));
    }

    @GetMapping
    @Operation(summary = "List admin place import jobs with status and error information")
    public ResponseEntity listJobs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<PlaceImportJobResponse> response = placeImportJobService.adminListJobs(userId, status, page, size);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get admin place import job details and item errors")
    public ResponseEntity getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(placeImportJobService.adminGetJob(jobId)));
    }
}
