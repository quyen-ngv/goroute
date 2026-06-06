package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.entity.*;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewFraudDetectionService;
import com.ds.goroute.type.UserTier;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final UserReviewRepository reviewRepository;
    private final UserReviewProfileRepository profileRepository;
    private final PlaceScoreRepository scoreRepository;
    private final ReviewHelpfulVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;

    private final ReviewScoringService scoringService;
    private final ReviewFraudDetectionService fraudDetectionService;

    /**
     * Create a new review
     */
    @Override
    @Transactional
    public UserReviewResponse createReview(UUID userId, CreateReviewRequest request) {
        // Check if user already reviewed this place
        Optional<UserReview> existingReview = reviewRepository.findByUserAndPlace(userId, request.getPlaceId());
        if (existingReview.isPresent()) {
            log.warn("User {} already reviewed place {}. Existing review ID: {}",
                userId, request.getPlaceId(), existingReview.get().getId());
            throw new BusinessException(ErrorConstant.REVIEW_ALREADY_EXISTS,
                "You have already reviewed this place. Please update your existing review instead.");
        }

        // Verify place exists
        Place place = placeRepository.findById(request.getPlaceId()).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));

        // Create review
        UserReview review = UserReview.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(request.getPlaceId())
                .tripId(null)
                .overallRating(request.getOverallRating())
                .foodRating(request.getFoodRating())
                .priceRating(request.getPriceRating())
                .ambianceRating(request.getAmbianceRating())
                .serviceRating(request.getServiceRating())
                .text(request.getText())
                .photos(request.getPhotos() != null ? JsonUtils.toJson(request.getPhotos()) : null)
                .weight(BigDecimal.ONE)
                .helpfulVotes(0)
                .unhelpfulVotes(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        reviewRepository.save(review);

        // Fraud detection
        fraudDetectionService.detectAndFlagReview(review);

        // Update user profile
        profileRepository.incrementReviewCount(userId);
        scoringService.updateUserTier(userId);

        // Recalculate place scores
        scoringService.recalculatePlaceScores(request.getPlaceId());

        return mapToResponse(review, userId);
    }

    /**
     * Update existing review
     */
    @Override
    @Transactional
    public UserReviewResponse updateReview(UUID userId, UUID reviewId, UpdateReviewRequest request) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        // Check ownership
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "You can only edit your own reviews");
        }

        // Update fields
        if (request.getOverallRating() != null) {
            review.setOverallRating(request.getOverallRating());
        }
        if (request.getFoodRating() != null) {
            review.setFoodRating(request.getFoodRating());
        }
        if (request.getPriceRating() != null) {
            review.setPriceRating(request.getPriceRating());
        }
        if (request.getAmbianceRating() != null) {
            review.setAmbianceRating(request.getAmbianceRating());
        }
        if (request.getServiceRating() != null) {
            review.setServiceRating(request.getServiceRating());
        }
        if (request.getText() != null) {
            review.setText(request.getText());
        }
        if (request.getPhotos() != null) {
            review.setPhotos(JsonUtils.toJson(request.getPhotos()));
        }

        review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.update(review);

        // Recalculate scores
        scoringService.recalculatePlaceScores(review.getPlaceId());

        return mapToResponse(review, userId);
    }

    /**
     * Delete review
     */
    @Override
    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "You can only delete your own reviews");
        }

        UUID placeId = review.getPlaceId();

        reviewRepository.delete(reviewId);
        profileRepository.decrementReviewCount(userId);
        scoringService.updateUserTier(userId);
        scoringService.recalculatePlaceScores(placeId);
    }

    /**
     * Get reviews for a place
     */
    @Override
    public List<UserReviewResponse> getPlaceReviews(UUID placeId, UUID currentUserId, int page, int size) {
        int offset = page * size;
        List<UserReview> reviews = reviewRepository.findByPlaceId(placeId, size, offset);

        return reviews.stream()
                .map(review -> mapToResponse(review, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * Get user's reviews
     */
    @Override
    public List<UserReviewResponse> getUserReviews(UUID userId, int page, int size) {
        int offset = page * size;
        List<UserReview> reviews = reviewRepository.findByUserId(userId, size, offset);

        return reviews.stream()
                .map(review -> mapToResponse(review, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserReviewResponse> getUserReviewsForProfile(UUID targetUserId, UUID viewerId, int page, int size) {
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));
        int offset = page * size;
        List<UserReview> reviews = reviewRepository.findByUserId(targetUserId, size, offset);

        return reviews.stream()
                .map(review -> mapToResponse(review, viewerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserReviewResponse> getFeedReviews(UUID currentUserId, int page, int size) {
        int offset = page * size;
        List<UserReview> reviews = reviewRepository.findFeedReviews(currentUserId, size, offset);

        return reviews.stream()
                .map(review -> mapToResponse(review, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * Vote review as helpful
     */
    @Override
    @Transactional
    public UserReviewResponse voteHelpful(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        if (review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Cannot vote on your own review");
        }

        ReviewHelpfulVote existingVote = voteRepository.findByReviewIdAndUserId(reviewId, userId);

        if (existingVote != null) {
            if (existingVote.isHelpful()) {
                // Already voted helpful, remove vote
                voteRepository.delete(reviewId, userId);
            } else {
                // Was unhelpful, change to helpful
                existingVote.setHelpful(true);
                voteRepository.update(existingVote);
            }
        } else {
            // New helpful vote
            ReviewHelpfulVote vote = ReviewHelpfulVote.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .isHelpful(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            voteRepository.save(vote);
        }

        return syncVoteCounts(review, userId);
    }

    @Override
    @Transactional
    public UserReviewResponse voteUnhelpful(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        if (review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Cannot vote on your own review");
        }

        ReviewHelpfulVote existingVote = voteRepository.findByReviewIdAndUserId(reviewId, userId);

        if (existingVote != null) {
            if (!existingVote.isHelpful()) {
                // Already voted unhelpful, remove vote
                voteRepository.delete(reviewId, userId);
            } else {
                // Was helpful, change to unhelpful
                existingVote.setHelpful(false);
                voteRepository.update(existingVote);
            }
        } else {
            // New unhelpful vote
            ReviewHelpfulVote vote = ReviewHelpfulVote.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .isHelpful(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            voteRepository.save(vote);
        }

        return syncVoteCounts(review, userId);
    }

    private UserReviewResponse syncVoteCounts(UserReview review, UUID currentUserId) {
        int helpfulCount = voteRepository.countByReviewIdAndIsHelpful(review.getId(), true);
        int unhelpfulCount = voteRepository.countByReviewIdAndIsHelpful(review.getId(), false);
        review.setHelpfulVotes(helpfulCount);
        review.setUnhelpfulVotes(unhelpfulCount);
        reviewRepository.updateVoteCounts(review);

        // Update reviewer's profile
        scoringService.updateUserTier(review.getUserId());

        return mapToResponse(review, currentUserId);
    }

    /**
     * Get place score
     */
    @Override
    public PlaceScoreResponse getPlaceScore(UUID placeId) {
        PlaceScore score = scoreRepository.findByPlaceId(placeId).orElse(null);

        if (score == null) {
            return PlaceScoreResponse.builder()
                    .placeId(placeId)
                    .reviewCount(0)
                    .displayLabel("Not enough reviews")
                    .useGoogleScore(true)
                    .build();
        }

        boolean useGoogle = score.getReviewCount() < 10;

        return PlaceScoreResponse.builder()
                .placeId(placeId)
                .tripmindScore(score.getTripmindScore())
                .googleScore(score.getGoogleScore())
                .reviewCount(score.getReviewCount())
                .foodScore(score.getFoodScore())
                .priceScore(score.getPriceScore())
                .ambianceScore(score.getAmbianceScore())
                .serviceScore(score.getServiceScore())
                .nationalityBreakdown(score.getNationalityBreakdown() != null ?
                        JsonUtils.fromJson(score.getNationalityBreakdown(), Map.class) : null)
                .displayScore(useGoogle && score.getGoogleScore() != null ?
                        score.getGoogleScore().toString() :
                        (score.getTripmindScore() != null ? score.getTripmindScore().toString() : "N/A"))
                .displayLabel(score.getReviewCount() < 10 ? "Preliminary" :
                        (score.getReviewCount() < 50 ? "TripMind Score" : "TripMind Score"))
                .useGoogleScore(useGoogle)
                .lastCalculatedAt(score.getLastCalculatedAt())
                .build();
    }

    /**
     * Get user review profile
     */
    @Override
    public UserReviewProfileResponse getUserProfile(UUID userId) {
        UserReviewProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));

        return UserReviewProfileResponse.builder()
                .userId(userId)
                .tier(profile.getTier())
                .trustScore(profile.getTrustScore())
                .reviewCount(profile.getReviewCount())
                .avgReviewLength(profile.getAvgReviewLength())
                .helpfulVotesReceived(profile.getHelpfulVotesReceived())
                .verifiedTripsCount(profile.getVerifiedTripsCount())
                .tierDisplay(getTierDisplay(profile.getTier()))
                .tierDescription(getTierDescription(profile.getTier()))
                .build();
    }

    private UserReviewResponse mapToResponse(UserReview review, UUID currentUserId) {
        User user = userRepository.findById(review.getUserId()).orElse(null);
        UserReviewProfile profile = profileRepository.findByUserId(review.getUserId()).orElse(null);
        Place place = placeRepository.findById(review.getPlaceId()).orElse(null);

        // Get current user's vote status: true = helpful, false = unhelpful, null = no vote
        Boolean hasVotedHelpful = null;
        if (currentUserId != null) {
            ReviewHelpfulVote vote = voteRepository.findByReviewIdAndUserId(review.getId(), currentUserId);
            if (vote != null) {
                hasVotedHelpful = vote.isHelpful();
            }
        }

        return UserReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .placeId(review.getPlaceId())
                .tripId(review.getTripId())
                .placeName(place != null ? place.getTitle() : null)
                .placeAddress(place != null ? place.getAddress() : null)
                .placeThumbnail(place != null ? place.getThumbnail() : null)
                .placeReviewCount(place != null ? place.getReviewCount() : null)
                .placeReviewRating(place != null ? place.getReviewRating() : null)
                .placeCategory(place != null ? place.getCategory() : null)
                .placeGroup(place != null ? place.getPlaceGroup() : null)
                .placeLatitude(place != null ? place.getLatitude() : null)
                .placeLongitude(place != null ? place.getLongitude() : null)
                .placePhone(place != null ? place.getPhone() : null)
                .placeWebsite(place != null ? place.getWebsite() : null)
                .placePriceRange(place != null ? place.getPriceRange() : null)
                .placeVisitDurationMinutes(place != null ? place.getVisitDurationMinutes() : null)
                .userName(user != null ? user.getFullName() : "Unknown")
                .userAvatar(user != null ? user.getAvatarUrl() : null)
                .userTier(profile != null ? profile.getTier() : UserTier.NEWCOMER)
                .overallRating(review.getOverallRating())
                .foodRating(review.getFoodRating())
                .priceRating(review.getPriceRating())
                .ambianceRating(review.getAmbianceRating())
                .serviceRating(review.getServiceRating())
                .text(review.getText())
                .photos(review.getPhotos() != null ? JsonUtils.fromJson(review.getPhotos(), List.class) : null)
                .weight(review.getWeight())
                .helpfulVotes(review.getHelpfulVotes())
                .unhelpfulVotes(review.getUnhelpfulVotes() != null ? review.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(hasVotedHelpful)
                .isOwnReview(currentUserId != null && review.getUserId().equals(currentUserId))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private String getTierDisplay(UserTier tier) {
        return switch (tier) {
            case NEWCOMER -> "Newcomer ðŸŒ±";
            case TRAVELER -> "Traveler âœˆï¸";
            case EXPLORER -> "Explorer ðŸ§­";
            case EXPERT -> "Local Expert ðŸŒŸ";
        };
    }

    private String getTierDescription(UserTier tier) {
        return switch (tier) {
            case NEWCOMER -> "0-2 reviews";
            case TRAVELER -> "3-9 reviews";
            case EXPLORER -> "10-29 reviews";
            case EXPERT -> "30+ reviews";
        };
    }
}
