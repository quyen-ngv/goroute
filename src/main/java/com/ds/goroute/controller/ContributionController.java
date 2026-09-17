package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.CheckContributionRequest;
import com.ds.goroute.dto.request.CreateContributionRequest;
import com.ds.goroute.dto.response.CheckContributionResponse;
import com.ds.goroute.dto.response.ContributionResponse;
import com.ds.goroute.dto.response.ContributedPlaceResponse;
import com.ds.goroute.dto.response.ContributorSummaryResponse;
import com.ds.goroute.service.PlaceContributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/contributions")
@RequiredArgsConstructor
public class ContributionController extends BaseController {

    private final PlaceContributionService contributionService;

    @PostMapping("/check")
    public ResponseEntity check(@Valid @RequestBody CheckContributionRequest request) {
        CheckContributionResponse response = contributionService.checkContribution(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping
    public ResponseEntity create(
            @Valid @RequestBody CreateContributionRequest request,
            @CurrentUser UUID userId) {
        ContributionResponse response = contributionService.createContribution(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{contributionId}")
    public ResponseEntity cancel(
            @PathVariable UUID contributionId,
            @CurrentUser UUID userId) {
        contributionService.cancelContribution(userId, contributionId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/me")
    public ResponseEntity listMine(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ContributionResponse> items = contributionService.getMyContributions(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/me/places")
    public ResponseEntity listMyPlaces(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ContributedPlaceResponse> items = contributionService.getMyContributedPlaces(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/places/{placeId}/contributors")
    public ResponseEntity listPlaceContributors(@PathVariable UUID placeId) {
        List<ContributorSummaryResponse> items = contributionService.getPlaceContributors(placeId);
        return ResponseEntity.ok(ofSucceeded(items));
    }
}
