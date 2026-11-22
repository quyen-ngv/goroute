package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.PrivacyZoneRequest;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.service.TrackService;
import com.ds.goroute.service.TrackSharingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own tracker data across trips: privacy zones (TR-23) and "delete every route I
 * recorded" (TR-24). Not behind the feature flag: a person can always remove their data.
 * Lifetime totals ({@code /track-stats}, P4) and the overview map ({@code GET /tracks}) are, like
 * every other read of the tracker.
 */
@RestController
@RequestMapping("/v1/api/me")
@RequiredArgsConstructor
@Validated
public class TrackPrivacyController extends BaseController {

    private final TrackService trackService;
    private final TrackSharingService trackSharingService;

    @GetMapping("/privacy-zones")
    public ResponseEntity<BaseResponse<List<TrackResponses.Zone>>> privacyZones(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.privacyZones(userId)));
    }

    @PostMapping("/privacy-zones")
    public ResponseEntity<BaseResponse<TrackResponses.Zone>> createPrivacyZone(
            @Valid @RequestBody PrivacyZoneRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(trackService.createPrivacyZone(userId, request)));
    }

    @PutMapping("/privacy-zones/{zoneId}")
    public ResponseEntity<BaseResponse<TrackResponses.Zone>> updatePrivacyZone(
            @PathVariable UUID zoneId,
            @Valid @RequestBody PrivacyZoneRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.updatePrivacyZone(userId, zoneId, request)));
    }

    @DeleteMapping("/privacy-zones/{zoneId}")
    public ResponseEntity<BaseResponse<Void>> deletePrivacyZone(
            @PathVariable UUID zoneId,
            @CurrentUser UUID userId) {
        trackService.deletePrivacyZone(userId, zoneId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Distance, trips and provinces passed over every trip the caller recorded. */
    @GetMapping("/track-stats")
    public ResponseEntity<BaseResponse<TrackResponses.MyStats>> trackStats(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackSharingService.myStats(userId)));
    }

    /** Every trip the caller recorded in, each route thinned, for the profile's overview map. */
    @GetMapping("/tracks")
    public ResponseEntity<BaseResponse<List<TrackResponses.MyTripRoute>>> myRoutes(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackSharingService.myRoutes(userId)));
    }

    @DeleteMapping("/tracks")
    public ResponseEntity<BaseResponse<Void>> deleteAllTracks(@CurrentUser UUID userId) {
        trackService.deleteAllTracks(userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
