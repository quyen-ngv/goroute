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
import com.ds.goroute.repository.RefreshTokenRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.UserDeviceService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.UserGuideService;
import com.ds.goroute.service.UserService;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    // Feed "Top active users" scoring, set by product: a check-in is worth 1, or 2 with a
    // review; a public trip scores only past 5 activities, 1 per activity, capped at 10.
    private static final int TOP_ACTIVE_CHECKIN_POINTS = 1;
    private static final int TOP_ACTIVE_CHECKIN_WITH_REVIEW_POINTS = 2;
    private static final int TOP_ACTIVE_MIN_TRIP_ACTIVITIES = 5;
    private static final int TOP_ACTIVE_MAX_TRIP_POINTS = 10;
    private static final int TOP_ACTIVE_MAX_LIMIT = 20;
    /**
     * Rows the shared board keeps beyond the largest page, so a viewer who blocked (or was
     * blocked by) a few of the leaders still gets a full page once those are taken out.
     */
    private static final int TOP_ACTIVE_BOARD_HEADROOM = 20;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserGuideService guideService;
    private final UserReviewRepository userReviewRepository;
    private final TripRepository tripRepository;
    private final PlaceContributionMapper placeContributionMapper;
    private final FileUploadService fileUploadService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final UserDeviceService userDeviceService;
    private final com.ds.goroute.service.TrackService trackService;
    private final UserCheckinRepository userCheckinRepository;
    private final com.ds.goroute.service.UserBlockService userBlockService;
    /**
     * The board is the same for every viewer apart from blocks, and the Feed asks for it on
     * every open: computed once a minute per period, blocks applied per viewer afterwards.
     */
    private final com.github.benmanes.caffeine.cache.Cache<
            String, List<com.ds.goroute.dto.response.TopActiveUserResponse>> topActiveBoards =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofSeconds(60))
                    .maximumSize(16)
                    .build();

    @Override
    @Transactional
    public UserProfileResponse getProfile(UUID userId) {
        userRepository.updateLastLoginAt(userId);
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
        // Used to read every row of `users`, then run four count queries per surviving
        // row before throwing all but twenty away. Same candidates, same order (review
        // count desc, then newest first, which is what the stable sort over the
        // created_at DESC read produced), same counts — one statement.
        return userRepository.findDiscoverUsers(userId, resolvedLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.ds.goroute.dto.response.TopActiveUserResponse> getTopActiveUsers(
            UUID viewerId, com.ds.goroute.type.TopActivePeriod period, int limit) {
        int pageLimit = Math.max(1, Math.min(limit, TOP_ACTIVE_MAX_LIMIT));
        // Keyed by period and its start: a new day, week or month starts a new board, and DAY
        // and WEEK share a start every Monday.
        java.time.LocalDateTime since = period.startUtc(java.time.Instant.now());
        List<com.ds.goroute.dto.response.TopActiveUserResponse> board = topActiveBoards.get(
                period.name() + '@' + since,
                key -> List.copyOf(userRepository.findTopActiveUsers(
                        null,
                        since,
                        TOP_ACTIVE_CHECKIN_POINTS,
                        TOP_ACTIVE_CHECKIN_WITH_REVIEW_POINTS,
                        TOP_ACTIVE_MIN_TRIP_ACTIVITIES,
                        TOP_ACTIVE_MAX_TRIP_POINTS,
                        TOP_ACTIVE_MAX_LIMIT + TOP_ACTIVE_BOARD_HEADROOM)));
        java.util.Set<UUID> blocked = viewerId == null || board.isEmpty() || userBlockService == null
                ? java.util.Set.of()
                : userBlockService.blockedAmong(viewerId,
                        board.stream().map(com.ds.goroute.dto.response.TopActiveUserResponse::getId).toList());
        return board.stream()
                .filter(row -> !blocked.contains(row.getId()))
                .limit(pageLimit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> searchUsers(UUID userId, String query, int limit) {
        String term = query == null ? "" : query.trim();
        if (term.startsWith("@")) term = term.substring(1).trim();
        if (term.length() < 2) return List.of();
        if (term.length() > 50) term = term.substring(0, 50);
        String escaped = term.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return userRepository.searchUsers(userId, escaped, Math.max(1, Math.min(limit, 30)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> getFollowers(UUID userId) {
        ensureUserExists(userId);
        return userRepository.findFollowerProfiles(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscoverUserResponse> getFollowing(UUID userId) {
        ensureUserExists(userId);
        return userRepository.findFollowingProfiles(userId);
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

    /**
     * Avatars go through the shared upload door like every other image (MOD-05). They
     * used to reach storage directly with no size, type or content check at all, which
     * made them the widest hole in the product: an avatar renders on every review, every
     * comment and every feed row.
     */
    @Override
    @Transactional
    public String updateAvatar(UUID userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        ImageUploadOutcome outcome = fileUploadService.uploadImage(
                ImageUploadRequest.of(userId, ImageUploadRequest.ImageEntryPoint.USER_AVATAR,
                        "avatars/" + userId),
                file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection()
                            ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                            : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }

        String avatarUrl = outcome.url();
        user.setAvatarUrl(avatarUrl);
        userRepository.updateById(user);

        log.info("User avatar updated: {} -> {}", userId, avatarUrl);
        return avatarUrl;
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        imageStorageCleanupService.deleteImagesForEntityRecord("USER", userId);
        // Soft delete user
        userRepository.softDeleteById(userId);
        // The account is gone, so its refresh tokens must go with it: otherwise a
        // token held by anyone keeps minting access tokens for up to 30 more days.
        refreshTokenRepository.deleteByUserId(userId);
        // Its phones as well: a deleted account must stop receiving pushes, and a token left
        // behind would be handed to nobody until the phone registers for someone else.
        userDeviceService.deleteAllForUser(userId);
        // Recorded routes are location history; they go with the account, not with a delay.
        trackService.onUserDeleted(userId);

        log.info("User account soft deleted: {}", userId);
    }

    private void ensureUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
    }

    @Override
    @Transactional
    public com.ds.goroute.dto.response.BirthYearResponse updateBirthYear(UUID userId, int birthYear) {
        int currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        if (birthYear < 1900 || birthYear > currentYear) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_BIRTH_YEAR, "Birth year must be 1900.." + currentYear);
        }
        if (userRepository.updateBirthYear(userId, birthYear, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)) != 1) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found");
        }
        return new com.ds.goroute.dto.response.BirthYearResponse(birthYear);
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

        String guideBadge = guideBadgeFor(targetUserId);
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
                .checkinCount(stats.checkinCount())
                .rank(buildRankProgress(stats))
                .guide(guideBadge != null)
                .guideTitle(guideBadge)
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .build();
    }

    private UserProfileResponse buildUserProfileResponse(User user, ProfileStats stats) {
        String guideBadge = guideBadgeFor(user.getId());
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
                .lastLoginAt(user.getLastLoginAt())
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
                .checkinCount(stats.checkinCount())
                .rank(buildRankProgress(stats))
                .guide(guideBadge != null)
                .guideTitle(guideBadge)
                .build();
    }

    /**
     * The guide title to show, or null when this account is not a guide.
     *
     * <p>Returns the title rather than a boolean so one lookup answers both questions; an active
     * guide with no title still reads as a guide, because the empty title is the normal case.
     */
    private String guideBadgeFor(java.util.UUID userId) {
        return guideService.find(userId)
                .filter(com.ds.goroute.entity.UserGuideGrant::isActive)
                .map(grant -> grant.getDisplayTitle() == null ? "" : grant.getDisplayTitle())
                .orElse(null);
    }

    /**
     * @param includePrivateTrips the viewer is the profile's owner: their private trips and
     *                            check-ins count too, exactly as their own lists show them
     */
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
                userRepository.countFollowing(userId),
                // The same rows GET /checkins/users/{userId} pages through for this viewer.
                userCheckinRepository.countByUser(userId, includePrivateTrips)
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
            int followingCount,
            long checkinCount
    ) {
    }

    private record RankTier(String key, int minScore) {
    }
}
