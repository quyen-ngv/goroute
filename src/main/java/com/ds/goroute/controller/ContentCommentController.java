package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateContentCommentRequest;
import com.ds.goroute.dto.request.UpdateContentCommentRequest;
import com.ds.goroute.dto.response.ContentCommentPageResponse;
import com.ds.goroute.dto.response.ContentCommentResponse;
import com.ds.goroute.service.ContentCommentService;
import com.ds.goroute.type.ModeratedContentType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Comments and nested replies for trip, check-in, review and public collection posts. */
@RestController
@RequestMapping("/v1/api/content-comments")
@RequiredArgsConstructor
public class ContentCommentController extends BaseController {
    private final ContentCommentService service;

    @GetMapping
    public ResponseEntity<BaseResponse<ContentCommentPageResponse>> getComments(
            @RequestParam ModeratedContentType contentType,
            @RequestParam UUID contentId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.getComments(
                contentType, contentId, parentId, cursor, limit, userId)));
    }

    @GetMapping("/count")
    public ResponseEntity<BaseResponse<Integer>> getActiveCommentCount(
            @RequestParam ModeratedContentType contentType,
            @RequestParam UUID contentId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.getActiveCommentCount(contentType, contentId, userId)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ContentCommentResponse>> createComment(
            @Valid @RequestBody CreateContentCommentRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(service.createComment(request, userId)));
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<BaseResponse<ContentCommentResponse>> toggleLike(
            @PathVariable UUID commentId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.toggleLike(commentId, userId)));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{commentId}")
    public ResponseEntity<BaseResponse<ContentCommentResponse>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateContentCommentRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.updateComment(commentId, request, userId)));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<BaseResponse<Void>> deleteComment(@PathVariable UUID commentId,
                                                            @CurrentUser UUID userId) {
        service.deleteComment(commentId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
