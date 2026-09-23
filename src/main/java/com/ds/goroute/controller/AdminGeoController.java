package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.GeoBackfillResponse;
import com.ds.goroute.dto.response.GeoDatasetResponse;
import com.ds.goroute.dto.response.GeoProvinceResponse;
import com.ds.goroute.dto.response.GeoWardResponse;
import com.ds.goroute.service.GeoService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Province/ward pickers for the console, and the one-off backfill after a dataset load. */
@RestController
@RequestMapping("/v1/api/admin/geo")
@RequiredArgsConstructor
@Validated
public class AdminGeoController extends BaseController {

    private final GeoService geoService;

    @GetMapping("/dataset")
    @PreAuthorize("@adminAuthorization.can(authentication,'geo','get')")
    public ResponseEntity<BaseResponse<GeoDatasetResponse>> dataset() {
        return ResponseEntity.ok(ofSucceeded(geoService.dataset()));
    }

    @GetMapping("/provinces")
    @PreAuthorize("@adminAuthorization.can(authentication,'geo','get')")
    public ResponseEntity<BaseResponse<List<GeoProvinceResponse>>> provinces() {
        return ResponseEntity.ok(ofSucceeded(geoService.provinces()));
    }

    @GetMapping("/wards")
    @PreAuthorize("@adminAuthorization.can(authentication,'geo','get')")
    public ResponseEntity<BaseResponse<List<GeoWardResponse>>> wards(
            @RequestParam @Pattern(regexp = "^[0-9]{2}$") String provinceCode) {
        return ResponseEntity.ok(ofSucceeded(geoService.wardsOf(provinceCode)));
    }

    /**
     * Remaps the retired province codes and assigns wards. {@code reassignExisting} also
     * re-runs point-in-polygon for rows that already have a ward -- wanted after a
     * boundary correction, not needed after the first load.
     */
    @PostMapping("/backfill")
    @PreAuthorize("@adminAuthorization.can(authentication,'geo','update')")
    public ResponseEntity<BaseResponse<GeoBackfillResponse>> backfill(
            @RequestParam(defaultValue = "false") boolean reassignExisting) {
        return ResponseEntity.ok(ofSucceeded(geoService.backfill(reassignExisting)));
    }
}
