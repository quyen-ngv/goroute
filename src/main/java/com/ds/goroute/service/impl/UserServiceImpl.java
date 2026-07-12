package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateProfileRequest;
import com.ds.goroute.dto.request.UpdateSettingsRequest;
import com.ds.goroute.dto.response.DiscoverUserResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.dto.response.UserProfileResponse;
import com.ds.goroute.dto.response.UserRankProgressResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceContributionMapper;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.UserService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private static final int SOCIAL_LINK_KEY_MAX_LENGTH = 40;
    private static final int SOCIAL_LINK_VALUE_MAX_LENGTH = 255;
    private static final List<RankTier> RANK_TIERS = List.of(
            new RankTier("beginner", 0),
            new RankTier("traveller", 100),
            new RankTier("explorer", 300),
            new RankTier("local_guide", 700),
            new RankTier("super_traveller", 1500)
    );
    
    private final UserRepository userRepository;
    private final UserReviewRepository userReviewRepository;
    private final TripRepository tripRepository;
    private final PlaceContributionMapper placeContributionMapper;
    private final StorageService storageService;
    private final ImageStorageCleanupService imageStorageCleanupService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        ProfileStats stats = getProfileStats(userId, true);
        return buildUserProfileResponse(user, stats);
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
        if (request.getBio() != null) {
            user.setBio(blankToNull(request.getBio()));
        }
        if (request.getSocialLinks() != null) {
            user.setSocialLinks(toSocialLinksJson(cleanSocialLinks(request.getSocialLinks())));
        }
        if (Boolean.TRUE.equals(request.getCompleteOnboarding())) {
            user.setOnboardingCompleted(true);
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
            String oldAvatarUrl = user.getAvatarUrl();

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
            if (oldAvatarUrl != null && !oldAvatarUrl.equals(avatarUrl)) {
                storageService.deleteFile(oldAvatarUrl);
            }
            
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

        imageStorageCleanupService.deleteImagesForEntityRecord("USER", userId);
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
        ProfileStats stats = getProfileStats(targetUserId, isSelf);
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
                .bio(user.getBio())
                .socialLinks(parseSocialLinks(user.getSocialLinks()))
                .tripCount(stats.tripCount())
                .reviewCount(stats.reviewCount())
                .likeCount(stats.likeCount())
                .cloneCount(stats.cloneCount())
                .contributionCount(stats.contributionCount())
                .hiddenGemContributionCount(stats.hiddenGemContributionCount())
                .followersCount(stats.followersCount())
                .followingCount(stats.followingCount())
                .rank(buildRankProgress(stats))
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .build();
    }

    private UserProfileResponse buildUserProfileResponse(User user, ProfileStats stats) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .socialLinks(parseSocialLinks(user.getSocialLinks()))
                .defaultCurrency(user.getDefaultCurrency())
                .defaultTravelMode(user.getDefaultTravelMode())
                .locationTracking(user.getLocationTracking() != null ? user.getLocationTracking().toString() : null)
                .language(user.getLanguage())
                .theme(user.getTheme())
                .onboardingCompleted(user.getOnboardingCompleted())
                .tripCount(stats.tripCount())
                .reviewCount(stats.reviewCount())
                .likeCount(stats.likeCount())
                .cloneCount(stats.cloneCount())
                .contributionCount(stats.contributionCount())
                .hiddenGemContributionCount(stats.hiddenGemContributionCount())
                .followersCount(stats.followersCount())
                .followingCount(stats.followingCount())
                .rank(buildRankProgress(stats))
                .build();
    }

    private ProfileStats getProfileStats(UUID userId, boolean includePrivateTrips) {
        int tripCount = includePrivateTrips
                ? tripRepository.countTripsByOwnerId(userId)
                : tripRepository.countPublicTripsByOwnerId(userId);
        int reviewCount = userReviewRepository.countByUserId(userId);
        int reviewLikes = userReviewRepository.sumHelpfulVotesByUserId(userId);
        int tripLikes = tripRepository.sumHelpfulVotesByOwnerId(userId);
        int cloneCount = tripRepository.sumCopyCountByOwnerId(userId);
        int contributionCount = placeContributionMapper.countContributionsByUserId(userId);
        int hiddenGemContributionCount = placeContributionMapper.countContributedPlacesByUserId(userId);

        return new ProfileStats(
                tripCount,
                reviewCount,
                reviewLikes + tripLikes,
                cloneCount,
                contributionCount,
                hiddenGemContributionCount,
                userRepository.countFollowers(userId),
                userRepository.countFollowing(userId)
        );
    }

    private UserRankProgressResponse buildRankProgress(ProfileStats stats) {
        int score = calculateProfileScore(stats);
        RankTier current = RANK_TIERS.get(0);
        RankTier next = null;

        for (int i = 0; i < RANK_TIERS.size(); i++) {
            RankTier tier = RANK_TIERS.get(i);
            if (score >= tier.minScore()) {
                current = tier;
                next = i + 1 < RANK_TIERS.size() ? RANK_TIERS.get(i + 1) : null;
            } else {
                break;
            }
        }

        int nextScore = next != null ? next.minScore() : current.minScore();
        int pointsToNext = next != null ? Math.max(0, nextScore - score) : 0;
        double progress = next != null
                ? (double) (score - current.minScore()) / (nextScore - current.minScore())
                : 1.0;

        return UserRankProgressResponse.builder()
                .tierKey(current.key())
                .nextTierKey(next != null ? next.key() : null)
                .score(score)
                .currentTierScore(current.minScore())
                .nextTierScore(nextScore)
                .pointsToNextTier(pointsToNext)
                .progress(Math.max(0, Math.min(1, progress)))
                .build();
    }

    private int calculateProfileScore(ProfileStats stats) {
        return stats.reviewCount() * 20
                + stats.likeCount() * 5
                + stats.cloneCount() * 10
                + stats.contributionCount() * 30
                + stats.hiddenGemContributionCount() * 50;
    }

    private Map<String, String> parseSocialLinks(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        Map<String, String> links = JsonUtils.fromJson(rawJson, new TypeReference<Map<String, String>>() {});
        return links == null ? Map.of() : cleanSocialLinks(links);
    }

    private Map<String, String> cleanSocialLinks(Map<String, String> input) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        input.forEach((rawKey, rawValue) -> {
            String key = cleanSocialKey(rawKey);
            String value = blankToNull(rawValue);
            if (key != null && value != null) {
                cleaned.put(key, value.length() > SOCIAL_LINK_VALUE_MAX_LENGTH
                        ? value.substring(0, SOCIAL_LINK_VALUE_MAX_LENGTH)
                        : value);
            }
        });
        return cleaned;
    }

    private String cleanSocialKey(String rawKey) {
        String key = blankToNull(rawKey);
        if (key == null) {
            return null;
        }
        key = key.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
        return key.length() > SOCIAL_LINK_KEY_MAX_LENGTH
                ? key.substring(0, SOCIAL_LINK_KEY_MAX_LENGTH)
                : key;
    }

    private String toSocialLinksJson(Map<String, String> links) {
        return JsonUtils.toJson(links == null ? Map.of() : links);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ProfileStats(
            int tripCount,
            int reviewCount,
            int likeCount,
            int cloneCount,
            int contributionCount,
            int hiddenGemContributionCount,
            int followersCount,
            int followingCount
    ) {
    }

    private record RankTier(String key, int minScore) {
    }
}
