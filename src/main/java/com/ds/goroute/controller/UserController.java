package com.ds.goroute.controller;

import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.UserProfileResponse;
import com.ds.goroute.service.UserService;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
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
public class UserController extends BaseService {
    
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getProfile(
            @RequestAttribute("userId") UUID userId) {
        UserProfileResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(ofSucceeded(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestAttribute("userId") UUID userId) {
        UserProfileResponse profile = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ofSucceeded(profile));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<BaseResponse<Void>> updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            @RequestAttribute("userId") UUID userId) {
        userService.updateSettings(userId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/discover")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> discoverUsers(
            @RequestParam(defaultValue = "10") int limit,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.discoverUsers(userId, limit)));
    }

    @GetMapping("/me/followers")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getFollowers(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowers(userId)));
    }

    @GetMapping("/me/following")
    public ResponseEntity<BaseResponse<List<DiscoverUserResponse>>> getFollowing(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(userService.getFollowing(userId)));
    }

    @PostMapping("/{targetUserId}/follow")
    public ResponseEntity<BaseResponse<Void>> followUser(
            @PathVariable UUID targetUserId,
            @RequestAttribute("userId") UUID userId) {
        userService.followUser(userId, targetUserId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{targetUserId}/follow")
    public ResponseEntity<BaseResponse<Void>> unfollowUser(
            @PathVariable UUID targetUserId,
            @RequestAttribute("userId") UUID userId) {
        userService.unfollowUser(userId, targetUserId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<BaseResponse<String>> updateAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID userId) {
        String avatarUrl = userService.updateAvatar(userId, file);
        return ResponseEntity.ok(ofSucceeded(avatarUrl));
    }

    @DeleteMapping("/me")
    public ResponseEntity<BaseResponse<Void>> deleteAccount(
            @RequestAttribute("userId") UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
