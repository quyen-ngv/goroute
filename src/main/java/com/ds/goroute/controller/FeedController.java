package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.FeedFeedbackRequest;
import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.dto.response.FeedFeedbackResponse;
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
