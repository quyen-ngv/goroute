package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateCommentRequest;
import com.ds.goroute.dto.response.CommentResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}")
@RequiredArgsConstructor
@Slf4j
public class CommentController extends BaseService {

    private final CommentService commentService;

    /**
     * Comment counts for every activity of the trip.
     *
     * <p>One request for the whole itinerary rather than one per card: without this the
     * count badge on each activity would cost a round trip per row.
     */
    @GetMapping("/comment-counts")
    public ResponseEntity<BaseResponse<Map<UUID, Integer>>> getCommentCounts(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(commentService.getCommentCounts(tripId, userId)));
    }

    @GetMapping("/activities/{activityId}/comments")
    public ResponseEntity<BaseResponse<List<CommentResponse>>> getComments(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @RequestAttribute("userId") UUID userId) {
        List<CommentResponse> comments = commentService.getComments(tripId, activityId, userId);
        return ResponseEntity.ok(ofSucceeded(comments));
    }

    @PostMapping("/activities/{activityId}/comments")
    public ResponseEntity<BaseResponse<CommentResponse>> createComment(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @Valid @RequestBody CreateCommentRequest request,
            @RequestAttribute("userId") UUID userId) {
        CommentResponse comment = commentService.createComment(tripId, activityId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(comment));
    }

    @DeleteMapping("/activities/{activityId}/comments/{commentId}")
    public ResponseEntity<BaseResponse<Void>> deleteComment(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @PathVariable UUID commentId,
            @RequestAttribute("userId") UUID userId) {
        commentService.deleteComment(tripId, activityId, commentId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
