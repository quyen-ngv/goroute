package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.dto.response.UserProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.dto.response.TripResponse;
import com.ds.goroute.service.UserService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.dto.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController extends BaseController {
    
    private final UserService userService;
    private final TripService tripService;
    private final ReviewService reviewService;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getProfile(
            @CurrentUser UUID userId) {
        UserProfileResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(ofSucceeded(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @CurrentUser UUID userId) {
        UserProfileResponse profile = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ofSucceeded(profile));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<BaseResponse<Void>> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            @CurrentUser UUID userId) {
        userService.updateSettings(userId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/discover")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> discoverUsers(
            @RequestParam(defaultValue = "10") int limit,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.discoverUsers(userId, limit)));
    }

    @GetMapping("/me/followers")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getFollowers(
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowers(userId)));
    }

    @GetMapping("/me/following")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getFollowing(
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowing(userId)));
    }

    @PostMapping("/{targetUserId}/follow")
    public ResponseEntity<BaseResponse<Void>> followUser(
            @PathVariable UUID targetUserId,
            @CurrentUser UUID userId) {
        userService.followUser(userId, targetUserId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{targetUserId}/follow")
    public ResponseEntity<BaseResponse<Void>> unfollowUser(
            @PathVariable UUID targetUserId,
            @CurrentUser UUID userId) {
        userService.unfollowUser(userId, targetUserId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponse<PublicUserProfileResponse>> getPublicProfile(
            @PathVariable UUID userId,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(userService.getPublicProfile(userId, viewerId)));
    }

    @GetMapping("/{userId}/trips")
    public ResponseEntity<BaseResponse<List<TripResponse>>> getUserTrips(
            @PathVariable UUID userId,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(tripService.getProfileTrips(userId, viewerId)));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<BaseResponse<List<UserReviewResponse>>> getUserReviews(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @CurrentUser(required = false) UUID viewerId) {
        return ResponseEntity.ok(ofSucceeded(
                reviewService.getUserReviewsForProfile(userId, viewerId, page, size)));
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getUserFollowers(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowers(userId)));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getUserFollowing(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowing(userId)));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<BaseResponse<String>> updateAvatar(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UUID userId) {
        String avatarUrl = userService.updateAvatar(userId, file);
        return ResponseEntity.ok(ofSucceeded(avatarUrl));
    }

    @DeleteMapping("/me")
    public ResponseEntity<BaseResponse<Void>> deleteAccount(
            @CurrentUser UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
