package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminCreatePostRequest;
import com.ds.goroute.dto.request.HidePostRequest;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.service.PostAdminService;
import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Posts in the console (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §6). Writes are for posts of
 * accounts still managed by the team (ACCOUNT_NOT_MANAGED, 409, otherwise); hide/show applies to
 * every post. The app's POST switches do not apply here.
 */
@RestController
@RequestMapping("/v1/api/admin/posts")
@RequiredArgsConstructor
@Validated
public class AdminPostController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostAdminService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','get')")
    public ResponseEntity<BaseResponse<SlicePageResponse<PostResponse>>> list(
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) PostType postType,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(defaultValue = "false") boolean managedOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(
                service.list(search, status, postType, authorUserId, managedOnly, page, size)));
    }

    @GetMapping("/{postId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','get')")
    public ResponseEntity<BaseResponse<PostResponse>> get(@PathVariable UUID postId) {
        return ResponseEntity.ok(ofSucceeded(service.get(postId)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','create')")
    public ResponseEntity<BaseResponse<PostResponse>> create(
            @Valid @RequestBody AdminCreatePostRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(service.create(adminId, request)));
    }

    @PutMapping("/{postId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostResponse>> update(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.update(adminId, postId, request)));
    }

    @PostMapping("/{postId}/publish")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostResponse>> publish(
            @PathVariable UUID postId,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.publish(adminId, postId)));
    }

    @PostMapping("/{postId}/unpublish")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostResponse>> unpublish(
            @PathVariable UUID postId,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.unpublish(adminId, postId)));
    }

    @DeleteMapping("/{postId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','delete')")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID postId,
            @CurrentUser UUID adminId) {
        service.delete(adminId, postId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping(value = "/{postId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostMediaResponse>> uploadMedia(
            @PathVariable UUID postId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "poster", required = false) MultipartFile poster,
            @RequestParam(value = "role", defaultValue = "BODY") PostMediaRole role,
            @RequestParam(value = "caption", required = false) @Size(max = 300) String caption,
            @RequestParam(value = "durationSeconds", required = false) @Min(0) Integer durationSeconds,
            @CurrentUser UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(
                service.uploadMedia(adminId, postId, file, poster, role, caption, durationSeconds)));
    }

    @DeleteMapping("/{postId}/media/{mediaId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<Void>> deleteMedia(
            @PathVariable UUID postId,
            @PathVariable UUID mediaId,
            @CurrentUser UUID adminId) {
        service.deleteMedia(adminId, postId, mediaId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Moderation takedown (content_takedowns, type POST); the author is told why. */
    @PostMapping("/{postId}/hide")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostResponse>> hide(
            @PathVariable UUID postId,
            @Valid @RequestBody HidePostRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.hide(adminId, postId, request)));
    }

    @PostMapping("/{postId}/show")
    @PreAuthorize("@adminAuthorization.can(authentication,'posts','update')")
    public ResponseEntity<BaseResponse<PostResponse>> show(
            @PathVariable UUID postId,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.show(adminId, postId)));
    }
}
