package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.SavePostRequest;
import com.ds.goroute.dto.response.SavedPostPageResponse;
import com.ds.goroute.service.SavedPostService;
import com.ds.goroute.type.SavedPostType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Other people's check-ins and reviews the signed-in user saved to read later (epic 06,
 * §2.6.2). Private to the saver; the author is never notified.
 */
@RestController
@Validated
@RequestMapping("/v1/api/saved-posts")
@RequiredArgsConstructor
public class SavedPostController extends BaseController {

    private final SavedPostService savedPostService;

    /** {@code sort} is {@code recent} (newest saved first) or {@code oldest}. */
    @GetMapping
    public ResponseEntity<BaseResponse<SavedPostPageResponse>> list(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "recent") @Pattern(regexp = "recent|oldest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ofSucceeded(
                savedPostService.list(userId, "oldest".equals(sort), cursor, size)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<Void>> save(
            @CurrentUser UUID userId,
            @Valid @RequestBody SavePostRequest request) {
        savedPostService.save(userId, request.getPostType(), request.getPostId());
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{postType}/{postId}")
    public ResponseEntity<BaseResponse<Void>> unsave(
            @CurrentUser UUID userId,
            @PathVariable SavedPostType postType,
            @PathVariable UUID postId) {
        savedPostService.unsave(userId, postType, postId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
