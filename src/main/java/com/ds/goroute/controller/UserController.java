package com.ds.goroute.controller;

import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
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

    @PostMapping("/me/avatar")
    public ResponseEntity<BaseResponse<String>> updateAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID userId) {
        String avatarUrl = userService.updateAvatar(userId, file);
        return ResponseEntity.ok(ofSucceeded(avatarUrl));
    }
}
