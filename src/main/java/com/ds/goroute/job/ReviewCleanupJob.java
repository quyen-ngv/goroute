package com.ds.goroute.job;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.PlaceReviewScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewCleanupJob {

    private final PlaceReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final PlaceReviewScoringService scoringService;

    private static final int MAX_REVIEWS_PER_PLACE = 200;

    /**
     * Clean up low-quality reviews for all places
     * Keep only top 200 reviews per place based on weighted score
     */
    @Async
    public void cleanupAllPlaces() {
        log.info("=== Starting Review Cleanup Job ===");
        long startTime = System.currentTimeMillis();

        List<Place> places = placeRepository.findAll();
        log.info("Found {} places to process", places.size());

        int totalDeleted = 0;
        int totalKept = 0;
        int placesProcessed = 0;

        for (Place place : places) {
            try {
                Map<String, Integer> result = cleanupPlaceReviews(place.getId());
                totalDeleted += result.get("deleted");
                totalKept += result.get("kept");
                placesProcessed++;

                if (placesProcessed % 100 == 0) {
                    log.info("Progress: {}/{} places processed, {} reviews deleted, {} kept",
                            placesProcessed, places.size(), totalDeleted, totalKept);
                }

            } catch (Exception e) {
                log.error("Failed to cleanup reviews for place {}: {}", place.getId(), e.getMessage());
            }
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info("=== Review Cleanup Completed in {}s ===", duration);
        log.info("Total: {} places processed, {} reviews deleted, {} reviews kept",
                placesProcessed, totalDeleted, totalKept);
    }

    /**
     * Clean up reviews for a single place
     * Returns map with "deleted" and "kept" counts
     */
    public Map<String, Integer> cleanupPlaceReviews(UUID placeId) {
        List<PlaceReview> reviews = reviewRepository.findByPlaceId(placeId);
        
        if (reviews.isEmpty()) {
            return Map.of("deleted", 0, "kept", 0);
        }

        int reviewCount = reviews.size();
        log.debug("Processing place {} with {} reviews", placeId, reviewCount);

        // Calculate weight for each review
        List<ReviewWithWeight> reviewsWithWeights = reviews.stream()
                .map(review -> {
                    double weight = scoringService.calculateReviewWeight(review, reviewCount);
                    return new ReviewWithWeight(review, weight);
                })
                .collect(Collectors.toList());

        // Sort by weight descending
        reviewsWithWeights.sort((a, b) -> Double.compare(b.weight, a.weight));

        // Keep top 200 reviews
        List<UUID> reviewsToKeep = reviewsWithWeights.stream()
                .limit(MAX_REVIEWS_PER_PLACE)
                .map(rw -> rw.review.getId())
                .collect(Collectors.toList());

        // Delete the rest
        List<UUID> reviewsToDelete = reviewsWithWeights.stream()
                .skip(MAX_REVIEWS_PER_PLACE)
                .map(rw -> rw.review.getId())
                .collect(Collectors.toList());

        if (!reviewsToDelete.isEmpty()) {
            reviewRepository.deleteByIds(reviewsToDelete);
            log.info("Place {}: Kept {} reviews, deleted {} reviews",
                    placeId, reviewsToKeep.size(), reviewsToDelete.size());
        }

        return Map.of(
                "deleted", reviewsToDelete.size(),
                "kept", reviewsToKeep.size()
        );
    }

    /**
     * Helper class to hold review and its weight
     */
    private static class ReviewWithWeight {
        final PlaceReview review;
        final double weight;

        ReviewWithWeight(PlaceReview review, double weight) {
            this.review = review;
            this.weight = weight;
        }
    }
}
