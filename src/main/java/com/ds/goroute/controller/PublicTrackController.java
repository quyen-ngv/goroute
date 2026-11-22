package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.service.TrackSharingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The recorded route shown next to a public trip (TR-21..TR-23), read by the app's public trip
 * screen and by the ondetour web page. Public under {@code /v1/api/public/**}; the trip's
 * visibility and each member's choice are checked in {@link TrackSharingService}.
 */
@RestController
@RequiredArgsConstructor
public class PublicTrackController extends BaseController {

    private final TrackSharingService trackSharingService;

    @GetMapping("/v1/api/public/trips/{tripId}/track")
    public ResponseEntity<BaseResponse<List<TrackResponses.PublicDay>>> publicTrack(
            @PathVariable UUID tripId,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(trackSharingService.publicTrack(viewerId, tripId)));
    }
}
