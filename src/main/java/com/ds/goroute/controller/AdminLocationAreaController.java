package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AssignLocationAreaRequest;
import com.ds.goroute.dto.response.LocationAreaAutoMapResponse;
import com.ds.goroute.dto.response.LocationAreaCoverageResponse;
import com.ds.goroute.enums.LocationAreaTarget;
import com.ds.goroute.service.LocationAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Operator view of which rows resolve to a curated tourist area, and the job that fills them. */
@RestController
@RequestMapping("/v1/api/admin/location-areas")
@RequiredArgsConstructor
public class AdminLocationAreaController {

    private final LocationAreaService locationAreaService;

    @GetMapping("/coverage")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity<BaseResponse<LocationAreaCoverageResponse>> coverage() {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(locationAreaService.coverage()));
    }

    /**
     * Defaults to a dry run: the caller has to ask for the write explicitly, because the
     * job touches every unmapped row across seven tables at once.
     */
    @PostMapping("/auto-map")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<LocationAreaAutoMapResponse>> autoMap(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(locationAreaService.autoMap(dryRun)));
    }

    /**
     * Corrects one row by hand. Guarded by the same permission as the auto-map job because
     * it writes the same column on the same tables, and {@code target} is an enum so the
     * caller can never name a table outside that whitelist.
     */
    @PutMapping("/{target}/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<UUID>> assign(@PathVariable LocationAreaTarget target,
                                                     @PathVariable UUID id,
                                                     @RequestBody AssignLocationAreaRequest request) {
        locationAreaService.assign(target, id, request.getLocationImageId());
        return ResponseEntity.ok(BaseResponse.ofSucceeded(request.getLocationImageId()));
    }
}
