package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.FeedFeedbackRequest;
import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.dto.response.FeedFeedbackResponse;
import com.ds.goroute.dto.response.FeedForYouPageResponse;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedForYouService;
import com.ds.goroute.type.FeedItemType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import com.ds.goroute.service.FeedFeedbackService;
import com.ds.goroute.service.FeedImpressionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Feed's behaviour signals (epic 12, .docs/FEED_RANKING_PLAN_2026-10-22.md): what the viewer
 * saw, and what they asked not to see. Signed-in only; a guest's app sends nothing.
 */
@RestController
@Validated
@RequestMapping("/v1/api/feed")
@RequiredArgsConstructor
public class FeedController extends BaseController {

    private final FeedImpressionService impressionService;
    private final FeedFeedbackService feedbackService;
    private final FeedForYouService forYouService;

    /**
     * The ranked "For you" feed (plan §4). The first call ranks once for this scroll; send the
     * returned {@code nextCursor} back for the following pages. {@code enabled = false} means
     * the viewer is not on the ranked feed yet and the app keeps its three-source feed.
     *
     * <p>{@code types}, {@code placeGroups}/{@code subTypes} and {@code latitude}/{@code longitude}/
     * {@code radiusKm} are the Feed's chips (hard filters). {@code ctxLat}/{@code ctxLng} is the
     * phone's last known position for the soft context boost; it is used for this request only
     * and never stored. {@code contextOff} turns the trip context off. {@code debug} adds the
     * score breakdown for USER.BETA_USER.
     */
    @GetMapping("/for-you")
    public ResponseEntity<BaseResponse<FeedForYouPageResponse>> forYou(
            @RequestParam(required = false) @Size(max = 200) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(FeedForYouService.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) List<FeedItemType> types,
            @RequestParam(required = false) List<String> placeGroups,
            @RequestParam(required = false) List<String> subTypes,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(defaultValue = "50") BigDecimal radiusKm,
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double ctxLat,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double ctxLng,
            @RequestParam(defaultValue = "false") boolean contextOff,
            // Trips the viewer turned the context line off for; the server checks the ongoing one.
            @RequestParam(required = false) @Size(max = 50) List<UUID> contextOffTrips,
            @RequestParam(defaultValue = "false") boolean debug,
            @CurrentUser(required = false) UUID userId) {
        FeedFilterSpec spec = FeedFilterSpec.of(types, FeedPlaceKind.of(placeGroups, subTypes),
                FeedGeoFilter.of(latitude, longitude, radiusKm));
        return ResponseEntity.ok(ofSucceeded(forYouService.page(
                userId, spec, cursor, size, ctxLat, ctxLng,
                contextOff, contextOffTrips == null ? java.util.Set.of() : java.util.Set.copyOf(contextOffTrips), debug)));
    }

    /** A batch of posts the viewer had on screen; at most 50 per call. */
    @PostMapping("/impressions")
    public ResponseEntity<BaseResponse<Void>> recordImpressions(
            @CurrentUser UUID userId,
            @Valid @RequestBody FeedImpressionRequest request) {
        impressionService.record(userId, request.getItems());
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** "Hide this post" or "hide posts from this person". */
    @PostMapping("/feedback")
    public ResponseEntity<BaseResponse<FeedFeedbackResponse>> hide(
            @CurrentUser UUID userId,
            @Valid @RequestBody FeedFeedbackRequest request) {
        return ResponseEntity.ok(ofSucceeded(feedbackService.hide(userId, request)));
    }

    /** Undo a hide. Succeeds when it is already undone too. */
    @DeleteMapping("/feedback/{feedbackId}")
    public ResponseEntity<BaseResponse<Void>> unhide(
            @CurrentUser UUID userId,
            @PathVariable UUID feedbackId) {
        feedbackService.unhide(userId, feedbackId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
