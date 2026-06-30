package com.ds.goroute.controller;

import com.ds.goroute.dto.request.ContributionImportRequest;
import com.ds.goroute.dto.response.ContributionImportResponse;
import com.ds.goroute.service.PlaceContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/internal/places/import")
@RequiredArgsConstructor
@Tag(name = "Internal Contribution Import", description = "Scrape service callback APIs")
public class InternalContributionImportController {

    private final PlaceContributionService contributionService;

    @PostMapping("/contribution")
    @Operation(summary = "Import scraped place and publish contribution reviews")
    public ResponseEntity<ContributionImportResponse> importContribution(
            @Valid @RequestBody ContributionImportRequest request) {
        ContributionImportResponse existing = contributionService.getImportResult(
                request.getContributionGroupId(),
                request.getGorouteJobId() != null ? request.getGorouteJobId()
                        : parseUuid(request.getJobId()));

        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(existing);
        }

        ContributionImportResponse response = contributionService.importContribution(request);
        return ResponseEntity.ok(response);
    }

    private java.util.UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
