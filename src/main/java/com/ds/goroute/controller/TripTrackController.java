package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AddTrackVisitToItineraryRequest;
import com.ds.goroute.dto.request.TrackDeletionRequest;
import com.ds.goroute.dto.request.UpdateTrackSettingsRequest;
import com.ds.goroute.dto.request.UploadTrackChunkRequest;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.service.TrackService;
import com.ds.goroute.service.TrackSharingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Travel tracker of one trip (epic 16). Contract: .docs/designs/TRAVEL_TRACKER_API_CONTRACT.md.
 * Membership, visibility and ownership are all checked in {@link TrackService} and
 * {@link TrackSharingService}.
 */
@RestController
@RequestMapping("/v1/api/trips/{tripId}/track")
@RequiredArgsConstructor
@Validated
public class TripTrackController extends BaseController {

    /** Day numbers are 1-based; 0 and N+1 are the padding days around a trip. */
    private static final int MAX_DAY_NUMBER = 400;

    private static final MediaType GPX = MediaType.parseMediaType("application/gpx+xml");

    private final TrackService trackService;
    private final TrackSharingService trackSharingService;

    @GetMapping("/context")
    public ResponseEntity<BaseResponse<TrackResponses.Context>> context(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.context(userId, tripId)));
    }

    /** 201 for a new chunk, 200 when the same {@code clientChunkId} was already stored. */
    @PostMapping("/chunks")
    public ResponseEntity<BaseResponse<TrackResponses.ChunkUpload>> uploadChunk(
            @PathVariable UUID tripId,
            @Valid @RequestBody UploadTrackChunkRequest request,
            @CurrentUser UUID userId) {
        TrackService.UploadOutcome outcome = trackService.uploadChunk(userId, tripId, request);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ofSucceeded(outcome.result()));
    }

    @GetMapping("/days")
    public ResponseEntity<BaseResponse<List<TrackResponses.Day>>> days(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @Min(0) @Max(MAX_DAY_NUMBER) Integer dayNumber,
            @CurrentUser UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(trackService.days(viewerId, tripId, userId, dayNumber)));
    }

    @GetMapping("/overview")
    public ResponseEntity<BaseResponse<List<TrackResponses.Day>>> overview(
            @PathVariable UUID tripId,
            @RequestParam(required = false) @Min(0) @Max(MAX_DAY_NUMBER) Integer dayNumber,
            @CurrentUser UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(trackService.overview(viewerId, tripId, dayNumber)));
    }

    /** The caller's own raw points of one day, for the route editor. */
    @GetMapping("/days/{date}/points")
    public ResponseEntity<BaseResponse<TrackResponses.DayPoints>> dayPoints(
            @PathVariable UUID tripId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.dayPoints(userId, tripId, date)));
    }

    @PostMapping("/deletions")
    public ResponseEntity<BaseResponse<TrackResponses.Deletion>> delete(
            @PathVariable UUID tripId,
            @Valid @RequestBody TrackDeletionRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.delete(userId, tripId, request)));
    }

    @GetMapping("/settings")
    public ResponseEntity<BaseResponse<TrackResponses.Settings>> settings(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.settings(userId, tripId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<BaseResponse<TrackResponses.Settings>> updateSettings(
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTrackSettingsRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.updateSettings(userId, tripId, request)));
    }

    /** Hides one of the caller's own off-plan stops (P2). */
    @PostMapping("/visits/{visitId}/dismiss")
    public ResponseEntity<BaseResponse<TrackResponses.Visit>> dismissVisit(
            @PathVariable UUID tripId,
            @PathVariable UUID visitId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackService.dismissVisit(userId, tripId, visitId)));
    }

    /** 201 with the new activity; 200 with the earlier one when the stop was already added. */
    @PostMapping("/visits/{visitId}/add-to-itinerary")
    public ResponseEntity<BaseResponse<TrackResponses.AddedToItinerary>> addVisitToItinerary(
            @PathVariable UUID tripId,
            @PathVariable UUID visitId,
            @Valid @RequestBody(required = false) AddTrackVisitToItineraryRequest request,
            @CurrentUser UUID userId) {
        TrackService.AddOutcome outcome = trackService.addVisitToItinerary(userId, tripId, visitId, request);
        return ResponseEntity.status(outcome.alreadyAdded() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ofSucceeded(outcome.result()));
    }

    /**
     * The caller's own route as a GPX 1.1 file. The one tracker endpoint whose success body is
     * not a {@code BaseResponse}: it is a file download. Errors keep the usual JSON shape.
     */
    @GetMapping("/export.gpx")
    public ResponseEntity<byte[]> exportGpx(
            @PathVariable UUID tripId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentUser UUID userId) {
        TrackSharingService.GpxExport export = trackSharingService.exportGpx(userId, tripId, date);
        return ResponseEntity.ok()
                .contentType(GPX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }

    /**
     * The caller's own route of the trip, or of one day, cut the way outsiders see it: for a
     * picture of the route the caller shares themselves.
     */
    @GetMapping("/share-route")
    public ResponseEntity<BaseResponse<TrackResponses.ShareRoute>> shareRoute(
            @PathVariable UUID tripId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trackSharingService.shareRoute(userId, tripId, date)));
    }

    /** One member's totals for this trip; same access rule as {@code /days}. */
    @GetMapping("/recap")
    public ResponseEntity<BaseResponse<TrackResponses.Recap>> recap(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID userId,
            @CurrentUser UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(trackSharingService.recap(viewerId, tripId, userId)));
    }
}
