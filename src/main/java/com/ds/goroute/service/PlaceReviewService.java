package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.exception.BusinessError;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.type.ReviewLanguage;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final ImageMigrationService imageMigrationService;
    private final StorageService storageService;
    private final PlaceReviewScoringService scoringService;

    /**
     * Batch insert reviews from crawler data
     * Auto-cleanup logic: If any review has external image URL, delete all existing reviews and re-import
     */
    @Transactional
    public Map<String, Object> batchInsertReviews(List<ReviewInput> reviewInputs) {
        if (reviewInputs == null || reviewInputs.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,"Reviews list cannot be empty");
        }

        int totalInput = reviewInputs.size();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int filtered = 0;
        int failed = 0;
        int deleted = 0;
        List<String> errors = new ArrayList<>();

        // Group by googlePlaceId to validate places exist
        Map<String, List<ReviewInput>> reviewsByPlace = reviewInputs.stream()
                .collect(Collectors.groupingBy(ReviewInput::getGooglePlaceId));

        // Validate all places exist
        Map<String, UUID> placeIdMap = new HashMap<>();
        for (String googlePlaceId : reviewsByPlace.keySet()) {
            Place place = placeRepository.findByPlaceId(googlePlaceId);
            if (place == null) {
                errors.add("Place not found for googlePlaceId: " + googlePlaceId);
                failed += reviewsByPlace.get(googlePlaceId).size();
            } else {
                placeIdMap.put(googlePlaceId, place.getId());
            }
        }

        // === STEP 1: Deduplicate input first ===
        log.info("Deduplicating {} reviews", reviewInputs.size());
        Map<String, ReviewInput> uniqueReviewsMap = new LinkedHashMap<>();
        
        for (ReviewInput input : reviewInputs) {
            if (uniqueReviewsMap.containsKey(input.getReviewId())) {
                log.warn("Duplicate reviewId in input batch: {}, skipping", input.getReviewId());
                skipped++;
                continue;
            }
            uniqueReviewsMap.put(input.getReviewId(), input);
        }
        
        List<ReviewInput> uniqueReviews = new ArrayList<>(uniqueReviewsMap.values());
        log.info("After deduplication: {} unique reviews (skipped {} duplicates)", uniqueReviews.size(), skipped);
        
        // === STEP 2: Calculate authenticity scores for unique reviews ===
        log.info("Calculating authenticity scores for {} unique reviews", uniqueReviews.size());
        List<ReviewInputWithScore> reviewsWithScores = new ArrayList<>();
        
        for (ReviewInput input : uniqueReviews) {
            UUID placeId = placeIdMap.get(input.getGooglePlaceId());
            if (placeId == null) {
                continue; // Already counted as failed
            }
            
            // Calculate authenticity score using same logic as calculate-scores API
            double authenticityScore = calculateAuthenticityScoreFromInput(input);
            reviewsWithScores.add(new ReviewInputWithScore(input, authenticityScore, placeId));
        }
        
        // === STEP 3: Group by place and filter top 100 per place ===
        Map<String, List<ReviewInputWithScore>> scoresByPlace = reviewsWithScores.stream()
                .collect(Collectors.groupingBy(r -> r.input.getGooglePlaceId()));
        
        List<ReviewInputWithScore> filteredReviews = new ArrayList<>();
        
        for (Map.Entry<String, List<ReviewInputWithScore>> entry : scoresByPlace.entrySet()) {
            List<ReviewInputWithScore> placeReviews = entry.getValue();
            
            // Sort by authenticity score descending
            placeReviews.sort((a, b) -> Double.compare(b.authenticityScore, a.authenticityScore));
            
            // Take top 100 reviews per place
            int topN = Math.min(100, placeReviews.size());
            filteredReviews.addAll(placeReviews.subList(0, topN));
            
            int filteredCount = placeReviews.size() - topN;
            if (filteredCount > 0) {
                filtered += filteredCount;
                log.info("Place {}: Filtered out {} low-score reviews, keeping top {}", 
                    entry.getKey(), filteredCount, topN);
            }
        }
        
        log.info("After filtering: {} reviews remain (filtered out {})", filteredReviews.size(), filtered);
        
        // === STEP 4: Process filtered reviews (insert/update) ===
        List<PlaceReview> reviewsToInsert = new ArrayList<>();
        List<PlaceReview> reviewsToUpdate = new ArrayList<>();

        for (ReviewInputWithScore reviewWithScore : filteredReviews) {
            ReviewInput input = reviewWithScore.input;
            UUID placeId = reviewWithScore.placeId;
            
            try {
                // Check if review already exists by reviewId
                PlaceReview existingReview = reviewRepository.findByReviewId(input.getReviewId()).orElse(null);

                // Use original URLs (no migration)
                String originalProfile = input.getProfilePicture();
                List<String> originalUserImages = input.getUserImages();

                if (existingReview != null) {
                    // Update existing review (always update)
                    
                    // Keep existing images if already migrated to onestudy.id.vn
                    boolean keepProfile = shouldKeepExistingImage(existingReview.getProfilePicture());
                    boolean keepImages = shouldKeepExistingImages(existingReview.getImages());
                    
                    String profileToUse = keepProfile 
                        ? existingReview.getProfilePicture() 
                        : originalProfile;
                    
                    List<String> imagesToUse = keepImages
                        ? parseExistingImages(existingReview.getImages())
                        : originalUserImages;
                    
                    // Delete old images selectively
                    deleteOldReviewImagesSelectively(existingReview, keepProfile, keepImages);
                    
                    updateReviewFromInput(existingReview, input, placeId, profileToUse, imagesToUse);
                    
                    // Set authenticity score
                    existingReview.setAuthenticityScore(BigDecimal.valueOf(reviewWithScore.authenticityScore));
                    existingReview.setAuthenticityLevel(scoringService.getAuthenticityLevel(existingReview.getAuthenticityScore()));
                    existingReview.setScoreCalculatedAt(LocalDateTime.now());
                    
                    reviewsToUpdate.add(existingReview);
                    updated++;
                } else {
                    // Create new review
                    PlaceReview review = mapInputToReview(input, placeId, originalProfile, originalUserImages);
                    
                    // Set authenticity score
                    review.setAuthenticityScore(BigDecimal.valueOf(reviewWithScore.authenticityScore));
                    review.setAuthenticityLevel(scoringService.getAuthenticityLevel(review.getAuthenticityScore()));
                    review.setScoreCalculatedAt(LocalDateTime.now());
                    
                    reviewsToInsert.add(review);
                }

            } catch (Exception e) {
                log.error("Error processing review {}: {}", input.getReviewId(), e.getMessage());
                errors.add("Error processing review " + input.getReviewId() + ": " + e.getMessage());
                failed++;
            }
        }

        // Batch insert new reviews
        if (!reviewsToInsert.isEmpty()) {
            reviewRepository.insertBatch(reviewsToInsert);
            inserted = reviewsToInsert.size();
        }
        
        // Batch update existing reviews
        if (!reviewsToUpdate.isEmpty()) {
            reviewRepository.updateBatch(reviewsToUpdate);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalInput", totalInput);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("filtered", filtered);
        result.put("skipped", skipped);
        result.put("deleted", deleted);
        result.put("failed", failed);
        result.put("errors", errors);

        return result;
    }

    /**
     * Calculate authenticity score from ReviewInput (before saving to DB)
     * Same logic as calculateAuthenticityScore but works with ReviewInput
     */
    private double calculateAuthenticityScoreFromInput(ReviewInput input) {
        // has_text — weight 0.15
        String description = null;
        if (input.getReviewText() != null && !input.getReviewText().isEmpty()) {
            description = input.getReviewText().values().iterator().next();
        }
        double hasText = (description != null && !description.isEmpty()) ? 1.0 : 0.0;

        // text_length_score — weight 0.25
        double textLengthScore = 0.0;
        if (description != null) {
            textLengthScore = Math.min(description.length() / 200.0, 1.0);
        }

        // has_photos — weight 0.25
        double hasPhotos = (input.getUserImages() != null && !input.getUserImages().isEmpty()) ? 1.0 : 0.0;

        // reviewer_credibility_score — weight 0.35
        double r1 = Math.min((input.getTotalReviews() != null ? input.getTotalReviews() : 0) / 50.0, 1.0);
        double r2 = Math.min((input.getTotalPhotos() != null ? input.getTotalPhotos() : 0) / 100.0, 1.0);
        double r3 = (input.getIsLocalGuide() != null && input.getIsLocalGuide()) ? 1.0 : 0.0;
        double reviewerCredibility = (r1 + r2 + r3) / 3.0;

        // Final score
        double score = 0.15 * hasText
                     + 0.25 * textLengthScore
                     + 0.25 * hasPhotos
                     + 0.35 * reviewerCredibility;

        return score;
    }
    
    /**
     * Helper class to hold ReviewInput with its calculated authenticity score
     */
    private static class ReviewInputWithScore {
        final ReviewInput input;
        final double authenticityScore;
        final UUID placeId;

        ReviewInputWithScore(ReviewInput input, double authenticityScore, UUID placeId) {
            this.input = input;
            this.authenticityScore = authenticityScore;
            this.placeId = placeId;
        }
    }

    /**
     * Check if existing image should be kept (already migrated to onestudy.id.vn)
     */
    private boolean shouldKeepExistingImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }
        // Keep if already hosted on our server
        return imageUrl.contains("onestudy.id.vn");
    }
    
    /**
     * Check if existing images JSON should be kept (at least one image from onestudy.id.vn)
     */
    private boolean shouldKeepExistingImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isEmpty() || imagesJson.equals("[]")) {
            return false;
        }
        
        try {
            List<String> imageUrls = JsonUtils.fromJson(imagesJson, List.class);
            if (imageUrls != null && !imageUrls.isEmpty()) {
                // Keep if at least one image is from our server
                for (Object urlObj : imageUrls) {
                    String url = urlObj.toString();
                    if (url.contains("onestudy.id.vn")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse images JSON: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Parse existing images JSON to List
     */
    private List<String> parseExistingImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isEmpty() || imagesJson.equals("[]")) {
            return Collections.emptyList();
        }
        
        try {
            List<String> imageUrls = JsonUtils.fromJson(imagesJson, List.class);
            return imageUrls != null ? imageUrls : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to parse images JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if ReviewInput has external image URL (migration failed)
     */
    private boolean hasExternalImageUrlInInput(ReviewInput input) {
        // Check profile picture
        if (input.getProfilePicture() != null && 
            input.getProfilePicture().startsWith("http") && 
            !input.getProfilePicture().contains("onestudy.id.vn")) {
            return true;
        }
        
        // Check user images
        if (input.getUserImages() != null && !input.getUserImages().isEmpty()) {
            for (String url : input.getUserImages()) {
                if (url.startsWith("http") && !url.contains("onestudy.id.vn")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if review has external image URL (not from our MinIO)
     */
    private boolean hasExternalImageUrl(PlaceReview review) {
        // Check profile picture
        if (review.getProfilePicture() != null && 
            review.getProfilePicture().startsWith("http") && 
            !review.getProfilePicture().contains("onestudy.id.vn")) {
            return true;
        }
        
        // Check images JSON array
        if (review.getImages() != null && !review.getImages().equals("[]")) {
            try {
                List<String> imageUrls = JsonUtils.fromJson(review.getImages(), List.class);
                if (imageUrls != null) {
                    for (Object urlObj : imageUrls) {
                        String url = urlObj.toString();
                        if (url.startsWith("http") && !url.contains("onestudy.id.vn")) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse images JSON for review {}: {}", review.getReviewId(), e.getMessage());
            }
        }
        
        return false;
    }

    /**
     * Delete old images from S3 before updating review (selectively)
     */
    private void deleteOldReviewImagesSelectively(PlaceReview review, boolean keepProfile, boolean keepImages) {
        List<String> urlsToDelete = new ArrayList<>();
        
        // Delete old profile picture only if not keeping it
        if (!keepProfile && review.getProfilePicture() != null && 
            review.getProfilePicture().contains("onestudy.id.vn")) {
            urlsToDelete.add(review.getProfilePicture());
        }
        
        // Delete old user images only if not keeping them
        if (!keepImages && review.getImages() != null && !review.getImages().equals("[]")) {
            try {
                List<String> imageUrls = JsonUtils.fromJson(review.getImages(), List.class);
                if (imageUrls != null) {
                    for (Object urlObj : imageUrls) {
                        String url = urlObj.toString();
                        if (url.contains("onestudy.id.vn")) {
                            urlsToDelete.add(url);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse images JSON for deletion: {}", e.getMessage());
            }
        }
        
        // Batch delete from S3
        if (!urlsToDelete.isEmpty()) {
            try {
                storageService.deleteFiles(urlsToDelete);
                log.debug("Deleted {} old images from S3", urlsToDelete.size());
            } catch (Exception e) {
                log.error("Failed to delete old images from S3: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Delete old images from S3 before updating review
     */
    private void deleteOldReviewImages(PlaceReview review) {
        List<String> urlsToDelete = new ArrayList<>();
        
        // Add old profile picture
        if (review.getProfilePicture() != null && 
            review.getProfilePicture().contains("onestudy.id.vn")) {
            urlsToDelete.add(review.getProfilePicture());
        }
        
        // Add old user images
        if (review.getImages() != null && !review.getImages().equals("[]")) {
            try {
                List<String> imageUrls = JsonUtils.fromJson(review.getImages(), List.class);
                if (imageUrls != null) {
                    for (Object urlObj : imageUrls) {
                        String url = urlObj.toString();
                        if (url.contains("onestudy.id.vn")) {
                            urlsToDelete.add(url);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse images JSON for deletion: {}", e.getMessage());
            }
        }
        
        // Batch delete from S3
        if (!urlsToDelete.isEmpty()) {
            try {
                storageService.deleteFiles(urlsToDelete);
                log.debug("Deleted {} old images from S3", urlsToDelete.size());
            } catch (Exception e) {
                log.error("Failed to delete old images from S3: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Map ReviewInput to PlaceReview entity (with migrated images)
     */
    private PlaceReview mapInputToReview(ReviewInput input, UUID placeId, 
                                          String migratedProfilePicture, 
                                          List<String> migratedUserImages) {
        // Extract language and description from reviewText map
        String language = null;
        String description = null;

        if (input.getReviewText() != null && !input.getReviewText().isEmpty()) {
            Map.Entry<String, String> firstEntry = input.getReviewText().entrySet().iterator().next();
            language = firstEntry.getKey();
            description = firstEntry.getValue();
        }

        return PlaceReview.builder()
                .id(UUID.randomUUID())
                .placeId(placeId)
                .reviewId(input.getReviewId())
                .googlePlaceId(input.getGooglePlaceId())
                .reviewerName(input.getAuthorName())
                .profileUrl(input.getProfileUrl())
                .profilePicture(migratedProfilePicture)
                .isLocalGuide(input.getIsLocalGuide())
                .totalReviews(input.getTotalReviews())
                .totalPhotos(input.getTotalPhotos())
                .rating(input.getRating())
                .description(description)
                .language(mapLanguage(language))
                .reviewDate(parseReviewDate(input.getReviewDate()))
                .images(JsonUtils.toJson(migratedUserImages != null ? migratedUserImages : Collections.emptyList()))
                .likes(input.getLikes() != null ? input.getLikes() : 0)
                .contentHash(input.getContentHash())
                .isDeleted(input.getIsDeleted())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update existing review from input (with migrated images)
     */
    private void updateReviewFromInput(PlaceReview review, ReviewInput input, UUID placeId,
                                       String migratedProfilePicture, 
                                       List<String> migratedUserImages) {
        String language = null;
        String description = null;

        if (input.getReviewText() != null && !input.getReviewText().isEmpty()) {
            Map.Entry<String, String> firstEntry = input.getReviewText().entrySet().iterator().next();
            language = firstEntry.getKey();
            description = firstEntry.getValue();
        }

        review.setPlaceId(placeId);
        review.setReviewerName(input.getAuthorName());
        review.setProfileUrl(input.getProfileUrl());
        review.setProfilePicture(migratedProfilePicture);
        review.setIsLocalGuide(input.getIsLocalGuide());
        review.setTotalReviews(input.getTotalReviews());
        review.setTotalPhotos(input.getTotalPhotos());
        review.setRating(input.getRating());
        review.setDescription(description);
        review.setLanguage(mapLanguage(language));
        review.setReviewDate(parseReviewDate(input.getReviewDate()));
        review.setImages(JsonUtils.toJson(migratedUserImages != null ? migratedUserImages : Collections.emptyList()));
        review.setLikes(input.getLikes() != null ? input.getLikes() : 0);
        review.setContentHash(input.getContentHash());
        review.setIsDeleted(input.getIsDeleted());
        review.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Map language string to ReviewLanguage enum
     */
    private ReviewLanguage mapLanguage(String lang) {
        if (lang == null) {
            return ReviewLanguage.OTHER;
        }

        switch (lang.toLowerCase()) {
            case "vi":
                return ReviewLanguage.VI;
            case "en":
                return ReviewLanguage.EN;
            case "ja":
                return ReviewLanguage.JA;
            case "ko":
                return ReviewLanguage.KO;
            case "zh":
                return ReviewLanguage.ZH;
            default:
                return ReviewLanguage.OTHER;
        }
    }

    /**
     * Parse ISO 8601 date string to LocalDate
     * Handle both full precision and nanoseconds precision
     */
    private LocalDate parseReviewDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // First try direct OffsetDateTime parsing
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateStr);
            return offsetDateTime.toLocalDate();
        } catch (Exception e1) {
            try {
                // If fails, try LocalDateTime parsing and assume UTC
                LocalDateTime localDateTime = LocalDateTime.parse(dateStr);
                return localDateTime.toLocalDate();
            } catch (Exception e2) {
                try {
                    // If still fails, truncate nanoseconds and try again
                    String truncated = dateStr;
                    if (dateStr.contains(".") && dateStr.length() > 29) {
                        // Find the dot and keep only 6 digits after it (microseconds)
                        int dotIndex = dateStr.indexOf('.');
                        int endIndex = Math.min(dotIndex + 7, dateStr.length() - 1); // 6 digits + dot
                        truncated = dateStr.substring(0, endIndex) + dateStr.substring(dateStr.length() - 1);
                    }
                    LocalDateTime localDateTime = LocalDateTime.parse(truncated);
                    return localDateTime.toLocalDate();
                } catch (Exception e3) {
                    log.error("Error parsing review date after all attempts: {}", dateStr);
                    return LocalDate.now();  // Fallback to current date
                }
            }
        }
    }
}
