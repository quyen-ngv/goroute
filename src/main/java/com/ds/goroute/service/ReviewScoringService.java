package com.ds.goroute.service;

import com.ds.goroute.entity.PlaceScore;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.UserReviewProfile;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewFlagRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.type.UserTier;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewScoringService {

    private final UserReviewRepository reviewRepository;
    private final UserReviewProfileRepository profileRepository;
    private final PlaceScoreRepository scoreRepository;
    private final ReviewFlagRepository flagRepository;

    private static final double GLOBAL_MEAN = 3.8;
    private static final int MIN_VOTES = 10;
    private static final int TIME_DECAY_DAYS = 365;

    /**
     * Calculate weighted average score for a place
     */
    public BigDecimal calculatePlaceScore(UUID placeId) {
        List<UserReview> reviews = reviewRepository.findByPlaceId(placeId, 1000, 0);

        if (reviews.isEmpty()) {
            return null;
        }

        double weightedSum = 0;
        double totalWeight = 0;

        for (UserReview review : reviews) {
            double weight = calculateReviewWeight(review);
            weightedSum += review.getOverallRating() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            return null;
        }

        double rawScore = weightedSum / totalWeight;
        double bayesianScore = applyBayesianSmoothing(rawScore, reviews.size());

        return BigDecimal.valueOf(bayesianScore).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Calculate review weight based on user tier, trust score, flags, and time decay
     */
    public double calculateReviewWeight(UserReview review) {
        UserReviewProfile profile = profileRepository.findByUserId(review.getUserId())
                .orElse(createDefaultProfile(review.getUserId()));

        // Base weight from tier
        double baseWeight = profile.getTier().getBaseWeight();

        // Quality bonus from trust score (max +0.3)
        double qualityBonus = Math.min(profile.getTrustScore().doubleValue() / 100.0, 0.3);

        // Time decay
        double timeDecay = calculateTimeDecay(review.getCreatedAt());

        // Flag penalty
        int flagCount = flagRepository.countByReviewId(review.getId());
        double flagPenalty = flagCount >= 2 ? 0 : (flagCount == 1 ? 0.3 : 1.0);

        return (baseWeight + qualityBonus) * timeDecay * flagPenalty;
    }

    /**
     * Apply Bayesian smoothing to prevent low-sample bias
     */
    private double applyBayesianSmoothing(double rawScore, int reviewCount) {
        return (MIN_VOTES * GLOBAL_MEAN + reviewCount * rawScore) / (MIN_VOTES + reviewCount);
    }

    /**
     * Calculate time decay factor (exponential decay)
     */
    private double calculateTimeDecay(LocalDateTime createdAt) {
        long daysSince = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        return Math.exp(-daysSince / (double) TIME_DECAY_DAYS);
    }

    /**
     * Calculate aspect scores (food, price, ambiance, service)
     */
    public Map<String, BigDecimal> calculateAspectScores(UUID placeId) {
        List<UserReview> reviews = reviewRepository.findByPlaceId(placeId, 1000, 0);

        Map<String, Double> weightedSums = new HashMap<>();
        Map<String, Double> totalWeights = new HashMap<>();

        for (UserReview review : reviews) {
            double weight = calculateReviewWeight(review);

            if (review.getFoodRating() != null) {
                weightedSums.merge("food", review.getFoodRating() * weight, Double::sum);
                totalWeights.merge("food", weight, Double::sum);
            }
            if (review.getPriceRating() != null) {
                weightedSums.merge("price", review.getPriceRating() * weight, Double::sum);
                totalWeights.merge("price", weight, Double::sum);
            }
            if (review.getAmbianceRating() != null) {
                weightedSums.merge("ambiance", review.getAmbianceRating() * weight, Double::sum);
                totalWeights.merge("ambiance", weight, Double::sum);
            }
            if (review.getServiceRating() != null) {
                weightedSums.merge("service", review.getServiceRating() * weight, Double::sum);
                totalWeights.merge("service", weight, Double::sum);
            }
        }

        Map<String, BigDecimal> aspectScores = new HashMap<>();
        for (String aspect : weightedSums.keySet()) {
            double score = weightedSums.get(aspect) / totalWeights.get(aspect);
            aspectScores.put(aspect, BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP));
        }

        return aspectScores;
    }

    /**
     * Calculate user trust score
     */
    public BigDecimal calculateUserTrustScore(UUID userId) {
        UserReviewProfile profile = profileRepository.findByUserId(userId)
                .orElse(createDefaultProfile(userId));

        double reviewCountScore = Math.min(profile.getReviewCount() * 0.3, 30.0);
        double avgLengthScore = Math.min(profile.getAvgReviewLength() * 0.2 / 10.0, 20.0);
        double helpfulScore = Math.min(profile.getHelpfulVotesReceived() * 0.3, 30.0);
        double verifiedTripsScore = Math.min(profile.getVerifiedTripsCount() * 0.2 * 5, 20.0);

        double totalScore = reviewCountScore + avgLengthScore + helpfulScore + verifiedTripsScore;

        return BigDecimal.valueOf(Math.min(totalScore, 100.0)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Update user tier based on review count
     */
    public void updateUserTier(UUID userId) {
        UserReviewProfile profile = profileRepository.findByUserId(userId)
                .orElse(createDefaultProfile(userId));

        int reviewCount = reviewRepository.countByUserId(userId);
        UserTier newTier = UserTier.fromReviewCount(reviewCount);

        profile.setTier(newTier);
        profile.setReviewCount(reviewCount);
        profile.setTrustScore(calculateUserTrustScore(userId));

        profileRepository.update(profile);
    }

    /**
     * Recalculate all scores for a place
     */
    public void recalculatePlaceScores(UUID placeId) {
        Optional<PlaceScore> existingScore = scoreRepository.findByPlaceId(placeId);
        PlaceScore score = existingScore
                .orElse(PlaceScore.builder().placeId(placeId).build());

        // Calculate main score
        BigDecimal tripmindScore = calculatePlaceScore(placeId);
        score.setTripmindScore(tripmindScore);

        // Calculate aspect scores
        Map<String, BigDecimal> aspectScores = calculateAspectScores(placeId);
        score.setFoodScore(aspectScores.get("food"));
        score.setPriceScore(aspectScores.get("price"));
        score.setAmbianceScore(aspectScores.get("ambiance"));
        score.setServiceScore(aspectScores.get("service"));

        // Update review count
        int reviewCount = reviewRepository.countByPlaceId(placeId);
        score.setReviewCount(reviewCount);

        score.setLastCalculatedAt(LocalDateTime.now());

        if (existingScore.isPresent()) {
            scoreRepository.update(score);
        } else if (score.getTripmindScore() != null) {
            scoreRepository.save(score);
        }
    }

    private UserReviewProfile createDefaultProfile(UUID userId) {
        return UserReviewProfile.builder()
                .userId(userId)
                .tier(UserTier.NEWCOMER)
                .trustScore(BigDecimal.ZERO)
                .reviewCount(0)
                .avgReviewLength(0)
                .helpfulVotesReceived(0)
                .verifiedTripsCount(0)
                .build();
    }
}
