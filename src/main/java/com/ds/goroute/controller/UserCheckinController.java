package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.UserCheckinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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
public class UserCheckinController extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserCheckinService checkinService;

    /** What the composer should show once a place is picked, including any existing rating. */
    @GetMapping("/context")
    public ResponseEntity<BaseResponse<CheckinContextResponse>> context(
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) String locationKey,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.context(userId, placeId, locationKey)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<UserCheckinResponse>> create(
            @Valid @RequestBody CreateUserCheckinRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(checkinService.create(userId, request)));
    }

    @PutMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> update(
            @PathVariable UUID checkinId,
            @Valid @RequestBody UpdateUserCheckinRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.update(userId, checkinId, request)));
    }

    @DeleteMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID checkinId,
            @RequestAttribute("userId") UUID userId) {
        checkinService.delete(userId, checkinId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{checkinId}")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> get(
            @PathVariable UUID checkinId,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.get(userId, checkinId)));
    }

    @PostMapping("/{checkinId}/like")
    public ResponseEntity<BaseResponse<CheckinLikeResponse>> toggleLike(
            @PathVariable UUID checkinId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.toggleLike(userId, checkinId)));
    }

    /**
     * The feed pages on the timestamp of the last row seen rather than on a page number:
     * with new posts arriving while somebody scrolls, offset paging repeats some and skips
     * others, which is the single most noticeable way a feed looks broken.
     */
    @GetMapping("/feed")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> feed(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int limit,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(checkinService.feed(userId, before, limit)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<BaseResponse<List<UserCheckinResponse>>> byUser(
            @PathVariable("userId") UUID targetUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestAttribute(value = "userId", required = false) UUID viewerId) {
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
