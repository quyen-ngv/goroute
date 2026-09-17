package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;
import com.ds.goroute.service.UserCheckinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Check-in (epic 06).
 *
 * <p>One flow for every entry point -- the camera button during a trip, the "+" menu, and
 * the prompt at the top of the feed all post here with different context, rather than each
 * getting its own endpoint and its own subtly different rules.
 */
@RestController
@RequestMapping("/v1/api/checkins")
@RequiredArgsConstructor
@Validated
public class UserCheckinController extends BaseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserCheckinService checkinService;

    /** What the composer should show once a place is picked, including any existing rating. */
    @GetMapping("/context")
    public ResponseEntity<BaseResponse<CheckinContextResponse>> context(
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) String locationKey,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal accuracyMeters,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.context(
                userId, placeId, locationKey, latitude, longitude, accuracyMeters)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<UserCheckinResponse>> create(
            @Valid @RequestBody CreateUserCheckinRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(checkinService.create(userId, request)));
    }

    @PutMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> update(
            @PathVariable UUID checkinId,
            @Valid @RequestBody UpdateUserCheckinRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.update(userId, checkinId, request)));
    }

    @DeleteMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID checkinId,
            @RequestParam(defaultValue = "false") boolean deleteReview,
            @CurrentUser UUID userId) {
        checkinService.delete(userId, checkinId, deleteReview);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** "Have I already checked in here?" -- the author's own latest matching visit. */
    @GetMapping("/mine")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> mine(
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) UUID tripId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.findMine(userId, activityId, placeId, tripId)));
    }

    @GetMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> get(
            @PathVariable UUID checkinId,
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.get(userId, checkinId)));
    }

    @PostMapping("/{checkinId}/like")
    public ResponseEntity<BaseResponse<CheckinLikeResponse>> toggleLike(
            @PathVariable UUID checkinId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.toggleLike(userId, checkinId)));
    }

    /**
     * Pages by number rather than on the last row's timestamp: posts with photos come
     * first, so the order is not chronological and a timestamp cursor would skip rows. A
     * post arriving mid-scroll only pushes rows down, which the client de-duplicates by id.
     */
    @GetMapping("/feed")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> feed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.feed(userId, page, size)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> byUser(
            @PathVariable("userId") UUID targetUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.byUser(viewerId, targetUserId, page, size)));
    }

    @GetMapping("/places/{placeId}")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> byPlace(
            @PathVariable UUID placeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(checkinService.byPlace(placeId, page, size)));
    }

    /**
     * Check-ins at a spot that has no catalogue page. Without this, tapping the place name
     * on such a post would lead to an error screen.
     */
    @GetMapping("/locations/{locationKey}")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> byLocation(
            @PathVariable String locationKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(checkinService.byLocationKey(locationKey, page, size)));
    }
}
