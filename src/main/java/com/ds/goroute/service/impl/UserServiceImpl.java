package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.dto.response.UserProfileResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.UserService;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final UserReviewRepository userReviewRepository;
    private final TripRepository tripRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .defaultCurrency(user.getDefaultCurrency())
                .defaultTravelMode(user.getDefaultTravelMode())
                .locationTracking(user.getLocationTracking() != null ? user.getLocationTracking().toString() : null)
                .language(user.getLanguage())
                .theme(user.getTheme())
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getUsername() != null) {
            // Check username uniqueness
            userRepository.findByUsername(request.getUsername()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Username already taken");
                }
            });
            user.setUsername(request.getUsername());
        }

        userRepository.updateById(user);
        log.info("User profile updated: {}", userId);
        return getProfile(userId);
    }

    @Override
    @Transactional
    public void updateSettings(UUID userId, UpdateSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        if (request.getDefaultCurrency() != null) {
            user.setDefaultCurrency(request.getDefaultCurrency());
        }
        if (request.getDefaultTravelMode() != null) {
            user.setDefaultTravelMode(request.getDefaultTravelMode());
        }
        if (request.getLocationTracking() != null) {
            user.setLocationTracking(request.getLocationTracking());
        }
        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
        }
        if (request.getTheme() != null) {
            user.setTheme(request.getTheme());
        }

        userRepository.updateById(user);
        log.info("User settings updated: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> discoverUsers(UUID userId, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 20));
        Set<UUID> followingIds = userRepository.findFollowing(userId).stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        return userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !user.getId().equals(userId))
                .filter(user -> !followingIds.contains(user.getId()))
                .map(this::toDiscoverUserResponse)
                .sorted(Comparator.comparingInt(DiscoverUserResponse::getReviewCount).reversed())
                .limit(resolvedLimit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> getFollowers(UUID userId) {
        ensureUserExists(userId);
        return userRepository.findFollowers(userId).stream()
                .map(this::toDiscoverUserResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> getFollowing(UUID userId) {
        ensureUserExists(userId);
        return userRepository.findFollowing(userId).stream()
                .map(this::toDiscoverUserResponse)
                .toList();
    }

    @Override
    @Transactional
    public void followUser(UUID userId, UUID targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Cannot follow yourself");
        }
        ensureUserExists(userId);
        ensureUserExists(targetUserId);
        userRepository.follow(userId, targetUserId);
    }

    @Override
    @Transactional
    public void unfollowUser(UUID userId, UUID targetUserId) {
        ensureUserExists(userId);
        ensureUserExists(targetUserId);
        userRepository.unfollow(userId, targetUserId);
    }

    @Override
    @Transactional
    public String updateAvatar(UUID userId, MultipartFile file) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

            if (file.isEmpty()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "File is empty");
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String fileName = "avatars/" + userId + "/" + UUID.randomUUID() + extension;

            // Upload to S3
            String avatarUrl = storageService.uploadFile(
                    fileName,
                    file.getInputStream(),
                    file.getContentType(),
                    file.getSize()
            );
            
            user.setAvatarUrl(avatarUrl);
            userRepository.updateById(user);
            
            log.info("User avatar updated: {} -> {}", userId, avatarUrl);
            return avatarUrl;
            
        } catch (Exception e) {
            log.error("Failed to update avatar for user: {}", userId, e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Failed to upload avatar: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        // Soft delete user
        userRepository.softDeleteById(userId);
        
        log.info("User account soft deleted: {}", userId);
    }

    private void ensureUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
    }

    private DiscoverUserResponse toDiscoverUserResponse(User user) {
        return DiscoverUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .reviewCount(userReviewRepository.countByUserId(user.getId()))
                .followersCount(userRepository.countFollowers(user.getId()))
                .followingCount(userRepository.countFollowing(user.getId()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID targetUserId, UUID viewerId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        boolean isSelf = viewerId != null && viewerId.equals(targetUserId);
        int tripCount = isSelf
                ? tripRepository.countTripsByOwnerId(targetUserId)
                : tripRepository.countPublicTripsByOwnerId(targetUserId);
        boolean isFollowing = viewerId != null
                && !isSelf
                && userRepository.isFollowing(viewerId, targetUserId);
        boolean isFollowedBy = viewerId != null
                && !isSelf
                && userRepository.isFollowing(targetUserId, viewerId);

        return PublicUserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .tripCount(tripCount)
                .reviewCount(userReviewRepository.countByUserId(targetUserId))
                .followersCount(userRepository.countFollowers(targetUserId))
                .followingCount(userRepository.countFollowing(targetUserId))
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .build();
    }
}
