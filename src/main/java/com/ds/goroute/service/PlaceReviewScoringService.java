package com.ds.goroute.service;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.type.PlaceTrustLevel;
import com.ds.goroute.type.ReviewAuthenticityLevel;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewScoringService {

    private final PlaceReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );

    /**
     * Calculate authenticity score for a single review
     */
    public BigDecimal calculateAuthenticityScore(PlaceReview review) {
        // has_text â€” weight 0.15
        double hasText = (review.getDescription() != null && !review.getDescription().isEmpty()) ? 1.0 : 0.0;

        // text_length_score â€” weight 0.25
        double textLengthScore = 0.0;
        if (review.getDescription() != null) {
            textLengthScore = Math.min(review.getDescription().length() / 200.0, 1.0);
        }

        // has_photos â€” weight 0.25
        List<String> imageList = JsonUtils.fromJson(review.getImages(), List.class);
        double hasPhotos = (imageList != null && !imageList.isEmpty()) ? 1.0 : 0.0;

        // reviewer_credibility_score â€” weight 0.35
        double r1 = Math.min((review.getTotalReviews() != null ? review.getTotalReviews() : 0) / 50.0, 1.0);
        double r2 = Math.min((review.getTotalPhotos() != null ? review.getTotalPhotos() : 0) / 100.0, 1.0);
        double r3 = (review.getIsLocalGuide() != null && review.getIsLocalGuide()) ? 1.0 : 0.0;
        double reviewerCredibility = (r1 + r2 + r3) / 3.0;

        // Final score
        double score = 0.15 * hasText
                     + 0.25 * textLengthScore
                     + 0.25 * hasPhotos
                     + 0.35 * reviewerCredibility;

        return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Determine authenticity level from score
     */
    public ReviewAuthenticityLevel getAuthenticityLevel(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(0.80)) >= 0) {
            return ReviewAuthenticityLevel.HIGH;
        } else if (score.compareTo(BigDecimal.valueOf(0.50)) >= 0) {
            return ReviewAuthenticityLevel.MEDIUM;
        } else {
            return ReviewAuthenticityLevel.LOW;
        }
    }

    /**
     * Calculate place overall score
     */
    public Map<String, Object> calculatePlaceOverallScore(UUID placeId) {
        List<PlaceReview> reviews = reviewRepository.findByPlaceId(placeId);

        if (reviews.isEmpty()) {
            return null;
        }

        // Filter out deleted reviews
        reviews = reviews.stream()
                .filter(r -> r.getIsDeleted() == null || !r.getIsDeleted())
                .collect(Collectors.toList());

        if (reviews.isEmpty()) {
            return null;
        }

        // avg_authenticity_score â€” weight 0.50
        double avgAuth = reviews.stream()
                .filter(r -> r.getAuthenticityScore() != null)
                .mapToDouble(r -> r.getAuthenticityScore().doubleValue())
                .average()
                .orElse(0.0);

        // low_star_signal_score â€” weight 0.20
        long badCount = reviews.stream()
                .filter(r -> r.getRating() != null && r.getRating() <= 2
                        && r.getAuthenticityScore() != null
                        && r.getAuthenticityScore().compareTo(BigDecimal.valueOf(0.5)) >= 0)
                .count();

        double lowStarSignal;
        if (badCount == 0) {
            lowStarSignal = 1.0;
        } else if (badCount == 1) {
            lowStarSignal = 0.7;
        } else if (badCount == 2) {
            lowStarSignal = 0.4;
        } else {
            lowStarSignal = 0.0;
        }

        // distribution_score â€” weight 0.20
        int total = reviews.size();
        long fiveStarCount = reviews.stream().filter(r -> r.getRating() != null && r.getRating() == 5).count();
        long midStarCount = reviews.stream().filter(r -> r.getRating() != null && r.getRating() >= 2 && r.getRating() <= 4).count();

        double fiveStarRatio = (double) fiveStarCount / total;
        double midRatio = (double) midStarCount / total;
        boolean isJcurve = fiveStarRatio > 0.70 && midRatio < 0.10;
        double distributionScore = isJcurve ? 0.2 : 1.0;

        // spike_penalty_score â€” weight 0.10
        Map<LocalDate, Long> reviewsPerDay = reviews.stream()
                .filter(r -> r.getReviewDate() != null)
                .collect(Collectors.groupingBy(PlaceReview::getReviewDate, Collectors.counting()));

        boolean isSpike = false;
        if (!reviewsPerDay.isEmpty()) {
            long maxDay = reviewsPerDay.values().stream().max(Long::compare).orElse(0L);
            double avgDay = reviewsPerDay.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
            isSpike = maxDay > avgDay * 5;
        }
        double spikePenalty = isSpike ? 0.2 : 1.0;

        // place_overall_score
        double placeOverallScore = 0.50 * avgAuth
                                 + 0.20 * lowStarSignal
                                 + 0.20 * distributionScore
                                 + 0.10 * spikePenalty;

        Map<String, Object> result = new HashMap<>();
        result.put("avgAuthenticityScore", BigDecimal.valueOf(avgAuth).setScale(3, RoundingMode.HALF_UP));
        result.put("placeOverallScore", BigDecimal.valueOf(placeOverallScore).setScale(3, RoundingMode.HALF_UP));
        result.put("isJcurveDetected", isJcurve);
        result.put("isSpikeDetected", isSpike);
        result.put("authenticLowStarCount", (int) badCount);
        result.put("trustLevel", getTrustLevel(placeOverallScore));

        return result;
    }

    /**
     * Determine trust level from place overall score
     */
    public PlaceTrustLevel getTrustLevel(double score) {
        if (score >= 0.80) {
            return PlaceTrustLevel.TRUSTED;
        } else if (score >= 0.55) {
            return PlaceTrustLevel.MODERATE;
        } else if (score >= 0.30) {
            return PlaceTrustLevel.CAUTION;
        } else {
            return PlaceTrustLevel.SUSPICIOUS;
        }
    }

    /**
     * Calculate adjusted rating
     */
    public BigDecimal calculateAdjustedRating(BigDecimal reviewRating, BigDecimal placeOverallScore, int reviewCount) {
        if (reviewCount < 5) {
            return null;  // ChÆ°a Ä‘á»§ data
        }

        if (reviewRating == null || placeOverallScore == null) {
            return null;
        }

        return reviewRating.multiply(placeOverallScore).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Step 1: Calculate authenticity score for reviews without score
     */
    @Transactional
    public int calculateReviewAuthenticityScores(String googlePlaceId, boolean forceRecalculate) {
        List<PlaceReview> reviews;

        if (googlePlaceId != null) {
            Place place = placeRepository.findByPlaceId(googlePlaceId);
            if (place == null) {
                return 0;
            }
            reviews = reviewRepository.findByPlaceId(place.getId());
        } else {
            reviews = reviewRepository.findAll();
        }

        List<PlaceReview> reviewsToUpdate = reviews.stream()
                .filter(r -> forceRecalculate || r.getScoreCalculatedAt() == null
                        || (r.getUpdatedAt() != null && r.getScoreCalculatedAt().isBefore(r.getUpdatedAt())))
                .collect(Collectors.toList());

        if (reviewsToUpdate.isEmpty()) {
            return 0;
        }

        // Process in parallel
        List<CompletableFuture<Void>> futures = reviewsToUpdate.stream()
                .map(review -> CompletableFuture.runAsync(() -> {
                    try {
                        BigDecimal score = calculateAuthenticityScore(review);
                        ReviewAuthenticityLevel level = getAuthenticityLevel(score);

                        review.setAuthenticityScore(score);
                        review.setAuthenticityLevel(level);
                        review.setScoreCalculatedAt(LocalDateTime.now());

                        reviewRepository.update(review);
                    } catch (Exception e) {
                        log.error("Error calculating authenticity score for review {}: {}", review.getId(), e.getMessage());
                    }
                }, executorService))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return reviewsToUpdate.size();
    }

    /**
     * Step 2: Recalculate place overall scores
     */
    @Transactional
    public int recalculatePlaceScores(String googlePlaceId, boolean forceRecalculate) {
        List<Place> places;

        if (googlePlaceId != null) {
            Place place = placeRepository.findByPlaceId(googlePlaceId);
            if (place == null) {
                return 0;
            }
            places = Collections.singletonList(place);
        } else {
            places = placeRepository.findAll();
        }

        List<Place> placesToUpdate = places.stream()
                .filter(p -> forceRecalculate || p.getScoreCalculatedAt() == null)
                .collect(Collectors.toList());

        if (placesToUpdate.isEmpty()) {
            return 0;
        }

        // Process in parallel
        List<CompletableFuture<Void>> futures = placesToUpdate.stream()
                .map(place -> CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> scoreData = calculatePlaceOverallScore(place.getId());

                        if (scoreData != null) {
                            place.setAvgAuthenticityScore((BigDecimal) scoreData.get("avgAuthenticityScore"));
                            place.setPlaceOverallScore((BigDecimal) scoreData.get("placeOverallScore"));
                            place.setIsJcurveDetected((Boolean) scoreData.get("isJcurveDetected"));
                            place.setIsSpikeDetected((Boolean) scoreData.get("isSpikeDetected"));
                            place.setAuthenticLowStarCount((Integer) scoreData.get("authenticLowStarCount"));
                            place.setTrustLevel((PlaceTrustLevel) scoreData.get("trustLevel"));

                            BigDecimal adjustedRating = calculateAdjustedRating(
                                place.getReviewRating(),
                                place.getPlaceOverallScore(),
                                place.getReviewCount() != null ? place.getReviewCount() : 0
                            );
                            place.setAdjustedRating(adjustedRating);
                            place.setScoreCalculatedAt(LocalDateTime.now());

                            placeRepository.update(place);
                        }
                    } catch (Exception e) {
                        log.error("Error calculating place score for place {}: {}", place.getId(), e.getMessage());
                    }
                }, executorService))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return placesToUpdate.size();
    }

    /**
     * Calculate review weight for cleanup/ranking purposes
     * Higher weight = more valuable review (should be kept)
     * 
     * Factors:
     * - Authenticity score (40%)
     * - Review age (20%) - newer is better
     * - Helpful votes/likes (20%)
     * - Content quality (20%) - text length, has photos
     */
    public double calculateReviewWeight(PlaceReview review, int totalReviewCount) {
        // 1. Authenticity score (40%)
        double authenticityWeight = 0.0;
        if (review.getAuthenticityScore() != null) {
            authenticityWeight = review.getAuthenticityScore().doubleValue();
        }
        
        // 2. Review age (20%) - newer reviews get higher weight
        double ageWeight = 0.0;
        if (review.getReviewDate() != null) {
            long daysSinceReview = java.time.temporal.ChronoUnit.DAYS.between(
                review.getReviewDate().atStartOfDay(), 
                LocalDateTime.now()
            );
            // Reviews from last year get full weight, older reviews get less
            ageWeight = Math.max(0.0, 1.0 - (daysSinceReview / 365.0));
        }
        
        // 3. Helpful votes/likes (20%)
        double likesWeight = 0.0;
        if (review.getLikes() != null && review.getLikes() > 0) {
            // Normalize likes based on average likes for this place
            double avgLikes = Math.max(1.0, totalReviewCount / 10.0);
            likesWeight = Math.min(1.0, review.getLikes() / avgLikes);
        }
        
        // 4. Content quality (20%)
        double contentWeight = 0.0;
        
        // Text length score
        double textScore = 0.0;
        if (review.getDescription() != null && !review.getDescription().isEmpty()) {
            textScore = Math.min(1.0, review.getDescription().length() / 200.0);
        }
        
        // Has photos
        List<String> imageList = JsonUtils.fromJson(review.getImages(), List.class);
        double photoScore = (imageList != null && !imageList.isEmpty()) ? 1.0 : 0.0;
        
        contentWeight = (textScore + photoScore) / 2.0;
        
        // Calculate final weight
        double weight = 0.40 * authenticityWeight
                      + 0.20 * ageWeight
                      + 0.20 * likesWeight
                      + 0.20 * contentWeight;
        
        return weight;
    }

    /**
     * Full scoring job: Step 1 + Step 2
     */
    public Map<String, Integer> runFullScoringJob(String googlePlaceId, boolean forceRecalculate) {
        log.info("Starting full scoring job - googlePlaceId: {}, forceRecalculate: {}", googlePlaceId, forceRecalculate);

        // Step 1: Calculate review authenticity scores
        int reviewsUpdated = calculateReviewAuthenticityScores(googlePlaceId, forceRecalculate);
        log.info("Step 1 completed: {} reviews updated", reviewsUpdated);

        // Step 2: Recalculate place scores
        int placesUpdated = recalculatePlaceScores(googlePlaceId, forceRecalculate);
        log.info("Step 2 completed: {} places updated", placesUpdated);

        Map<String, Integer> result = new HashMap<>();
        result.put("reviewsUpdated", reviewsUpdated);
        result.put("placesUpdated", placesUpdated);

        return result;
    }
}
