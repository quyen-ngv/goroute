package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CheckContributionRequest;
import com.ds.goroute.dto.request.CreateContributionRequest;
import com.ds.goroute.dto.response.CheckContributionResponse;
import com.ds.goroute.dto.response.ContributionResponse;
import com.ds.goroute.dto.response.ContributedPlaceResponse;
import com.ds.goroute.dto.response.ContributorSummaryResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/contributions")
@RequiredArgsConstructor
@Tag(name = "Contributions", description = "User place contribution APIs")
public class ContributionController extends BaseService {

    private final PlaceContributionService contributionService;

    @PostMapping("/check")
    @Operation(summary = "Check if a Google Maps URL already exists or has a pending contribution")
    public ResponseEntity check(@Valid @RequestBody CheckContributionRequest request) {
        CheckContributionResponse response = contributionService.checkContribution(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping
    @Operation(summary = "Submit a new place contribution with pending review")
    public ResponseEntity create(
            @Valid @RequestBody CreateContributionRequest request,
            @RequestAttribute("userId") UUID userId) {
        ContributionResponse response = contributionService.createContribution(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{contributionId}")
    @Operation(summary = "Cancel a pending contribution")
    public ResponseEntity cancel(
            @PathVariable UUID contributionId,
            @RequestAttribute("userId") UUID userId) {
        contributionService.cancelContribution(userId, contributionId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/me")
    @Operation(summary = "List current user's contributions and their statuses")
    public ResponseEntity listMine(
            @RequestAttribute("userId") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ContributionResponse> items = contributionService.getMyContributions(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/me/places")
    @Operation(summary = "List places the user successfully contributed")
    public ResponseEntity listMyPlaces(
            @RequestAttribute("userId") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ContributedPlaceResponse> items = contributionService.getMyContributedPlaces(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/places/{placeId}/contributors")
    @Operation(summary = "List users who contributed a place")
    public ResponseEntity listPlaceContributors(@PathVariable UUID placeId) {
        List<ContributorSummaryResponse> items = contributionService.getPlaceContributors(placeId);
        return ResponseEntity.ok(ofSucceeded(items));
    }
}
