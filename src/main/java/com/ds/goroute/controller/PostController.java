package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.request.AttachPostMediaRequest;
import com.ds.goroute.dto.request.CreatePostRequest;
import com.ds.goroute.dto.response.PostTripMediaResponse;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostLikeResponse;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostPolicyResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.service.PostService;
import com.ds.goroute.type.PostMediaRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Blog posts in the app (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §5).
 *
 * <p>The reads (policy, feed, a place's or a person's posts, one post) answer guests too; see
 * SecurityConfig. Everything that writes needs the author, and writing a post also needs
 * POST.USER_WRITE_ENABLED or a beta account (POST_WRITE_NOT_ALLOWED, 403).
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
@Validated
public class PostController extends BaseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostService postService;

    /** What the app may show and offer, beta folded in. */
    @GetMapping("/posts/policy")
    public ResponseEntity<BaseResponse<PostPolicyResponse>> policy(@CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.policy(viewerId)));
    }

    /**
     * Published public posts, newest first, without the viewer's own. With latitude/longitude only
     * posts tagged with a place within {@code radiusKm} (capped at 1000 km).
     */
    @GetMapping("/posts/feed")
    public ResponseEntity<BaseResponse<SlicePageResponse<PostResponse>>> feed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @RequestParam(defaultValue = "50") BigDecimal radiusKm,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.feed(
                viewerId, FeedGeoFilter.of(latitude, longitude, radiusKm), page, size)));
    }

    /** Published public posts of the people the caller follows, newest first. */
    @GetMapping("/posts/following")
    public ResponseEntity<BaseResponse<SlicePageResponse<PostResponse>>> following(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.following(viewerId, page, size)));
    }

    /** Place Detail: the posts tagged with this place, newest first. */
    @GetMapping("/places/{placeId}/posts")
    public ResponseEntity<BaseResponse<SlicePageResponse<PostResponse>>> byPlace(
            @PathVariable UUID placeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.byPlace(viewerId, placeId, page, size)));
    }

    /** A profile's posts: the owner sees drafts too, anyone else the published public ones. */
    @GetMapping("/users/{userId}/posts")
    public ResponseEntity<BaseResponse<SlicePageResponse<PostResponse>>> byUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.byUser(viewerId, userId, page, size)));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<BaseResponse<PostResponse>> get(
            @PathVariable UUID postId,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(postService.get(viewerId, postId)));
    }

    /** {@code liked} is the state the viewer wants; without it the like flips. */
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<BaseResponse<PostLikeResponse>> like(
            @PathVariable UUID postId,
            @RequestParam(required = false) Boolean liked,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.setLike(userId, postId, liked)));
    }

    @PostMapping("/posts")
    public ResponseEntity<BaseResponse<PostResponse>> create(
            @Valid @RequestBody CreatePostRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(postService.create(userId, request)));
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<BaseResponse<PostResponse>> update(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.update(userId, postId, request)));
    }

    @PostMapping("/posts/{postId}/publish")
    public ResponseEntity<BaseResponse<PostResponse>> publish(
            @PathVariable UUID postId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.publish(userId, postId)));
    }

    @PostMapping("/posts/{postId}/unpublish")
    public ResponseEntity<BaseResponse<PostResponse>> unpublish(
            @PathVariable UUID postId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.unpublish(userId, postId)));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @PathVariable UUID postId,
            @CurrentUser UUID userId) {
        postService.delete(userId, postId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /**
     * One photo, or one video (MP4/MOV/WEBM, at most 50 MB and POST.MAX_VIDEO_SECONDS) with its
     * {@code poster} image. The body then shows it as {@code ![caption](media:<id>)}.
     */
    @PostMapping(value = "/posts/{postId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<PostMediaResponse>> uploadMedia(
            @PathVariable UUID postId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "poster", required = false) MultipartFile poster,
            @RequestParam(value = "role", defaultValue = "BODY") PostMediaRole role,
            @RequestParam(value = "caption", required = false) @Size(max = 300) String caption,
            @RequestParam(value = "durationSeconds", required = false) @Min(0) Integer durationSeconds,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(
                postService.uploadMedia(userId, postId, file, poster, role, caption, durationSeconds)));
    }

    @DeleteMapping("/posts/{postId}/media/{mediaId}")
    public ResponseEntity<BaseResponse<Void>> deleteMedia(
            @PathVariable UUID postId,
            @PathVariable UUID mediaId,
            @CurrentUser UUID userId) {
        postService.deleteMedia(userId, postId, mediaId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /**
     * The editor's "from the trip" tray: the author's own check-in photos and trip memories of the
     * story's trip, each matched to a stop, with the post's media id where the story uses one already.
     */
    @GetMapping("/posts/{postId}/trip-media")
    public ResponseEntity<BaseResponse<PostTripMediaResponse>> tripMedia(
            @PathVariable UUID postId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.tripMedia(userId, postId)));
    }

    /** "Start over": the trip story is written again from the trip as it is today; the author's text is lost. */
    @PostMapping("/posts/{postId}/reset-draft")
    public ResponseEntity<BaseResponse<PostResponse>> resetTripDraft(
            @PathVariable UUID postId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.resetTripDraft(userId, postId)));
    }

    /** Puts one item of that tray on the post as a shared media row; the file is not copied. */
    @PostMapping("/posts/{postId}/media/attach")
    public ResponseEntity<BaseResponse<PostMediaResponse>> attachTripMedia(
            @PathVariable UUID postId,
            @Valid @RequestBody AttachPostMediaRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(postService.attachTripMedia(userId, postId, request)));
    }

    /**
     * "Write about this trip": a TRIP_STORY draft from the itinerary and the caller's own check-ins
     * and memories, or the story the caller already has for this trip.
     */
    @PostMapping("/trips/{tripId}/post-draft")
    public ResponseEntity<BaseResponse<PostResponse>> tripDraft(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(postService.tripDraft(userId, tripId)));
    }
}
