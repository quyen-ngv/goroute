package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserProfileResponse getProfile(UUID userId);
    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);
    void updateSettings(UUID userId, UpdateSettingsRequest request);
    List<DiscoverUserResponse> discoverUsers(UUID userId, int limit);
    List<DiscoverUserResponse> getFollowers(UUID userId);
    List<DiscoverUserResponse> getFollowing(UUID userId);
    void followUser(UUID userId, UUID targetUserId);
    void unfollowUser(UUID userId, UUID targetUserId);
    String updateAvatar(UUID userId, MultipartFile file);
    void deleteAccount(UUID userId);
    PublicUserProfileResponse getPublicProfile(UUID targetUserId, UUID viewerId);
}
