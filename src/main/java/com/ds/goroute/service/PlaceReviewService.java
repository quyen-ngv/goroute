package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.dto.request.RefreshPlaceReviewsRequest;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewService {

    private static final int REFRESH_SAMPLE_LIMIT = 200;
    private static final int REFRESH_STORAGE_LIMIT = 30;

    private final PlaceReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final ImageMigrationService imageMigrationService;
    private final StorageService storageService;
    private final PlaceReviewScoringService scoringService;
    private final PlaceReviewScoreCalculator scoreCalculator;

    /**
     * Batch insert reviews from crawler data. Every persisted review image must be a
     * managed MinIO object; external URLs are compressed/migrated or discarded.
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
            double authenticityScore = scoreCalculator.authenticity(input).doubleValue();
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

                String targetPath = "places/" + input.getGooglePlaceId() + "/reviews/"
                        + storageSafeSegment(input.getReviewId()) + "/";

                if (existingReview != null) {
                    // Update existing review (always update)
                    boolean keepProfile = isManagedImage(existingReview.getProfilePicture());
                    List<String> existingManagedImages = managedImages(parseExistingImages(existingReview.getImages()));
                    boolean keepImages = !existingManagedImages.isEmpty();
                    String profileToUse = keepProfile
                            ? existingReview.getProfilePicture()
                            : migrateManagedImage(input.getProfilePicture(), targetPath + "profile/");
                    List<String> imagesToUse = keepImages
                            ? existingManagedImages
                            : migrateManagedImages(input.getUserImages(), targetPath);
                    
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
                    String profilePicture = migrateManagedImage(
                            input.getProfilePicture(), targetPath + "profile/");
                    List<String> userImages = migrateManagedImages(input.getUserImages(), targetPath);
                    PlaceReview review = mapInputToReview(input, placeId, profilePicture, userImages);
                    
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
     * Remove the currently stored crawler reviews before a fresh Google Maps scrape starts.
     * The refresh worker calls this for one ACTIVE place at a time.
     */
    @Transactional
    public Map<String, Object> prepareRefresh(UUID placeId) {
        Place place = requireActivePlace(placeId, null);
        List<PlaceReview> existingReviews = reviewRepository.findByPlaceId(placeId);

        reviewRepository.deleteByPlaceId(placeId);
        deleteManagedImages(existingReviews);

        clearPlaceReviewScore(place);
        place.setUpdatedAt(LocalDateTime.now());
        placeRepository.updateReviewRefreshMetadata(place);

        return Map.of(
                "placeId", placeId,
                "deleted", existingReviews.size(),
                "ready", true);
    }

    /**
     * Score at most 200 newest reviews that contain photos, persist at most 30 with the
     * highest authenticity score, and update last_scraped_at only after persistence succeeds.
     */
    @Transactional
    public Map<String, Object> completeRefresh(RefreshPlaceReviewsRequest request) {
        Place place = requireActivePlace(request.getPlaceId(), request.getGooglePlaceId());

        LinkedHashMap<String, ReviewInput> uniqueByReviewId = new LinkedHashMap<>();
        for (ReviewInput review : request.getReviews()) {
            if (uniqueByReviewId.size() >= REFRESH_SAMPLE_LIMIT) {
                break;
            }
            if (Boolean.TRUE.equals(review.getIsDeleted())
                    || review.getUserImages() == null
                    || review.getUserImages().isEmpty()
                    || !request.getGooglePlaceId().equals(review.getGooglePlaceId())) {
                continue;
            }
            uniqueByReviewId.putIfAbsent(review.getReviewId(), review);
        }
        List<ReviewInput> sample = new ArrayList<>(uniqueByReviewId.values());

        Comparator<ReviewInput> ranking = Comparator
                .comparing((ReviewInput review) -> scoreCalculator.authenticity(review), Comparator.reverseOrder())
                .thenComparing(review -> Objects.requireNonNullElse(review.getLikes(), 0), Comparator.reverseOrder())
                .thenComparing(review -> scoreCalculator.parseDate(review.getReviewDate()),
                        Comparator.nullsLast(Comparator.reverseOrder()));
        List<ReviewInput> ranked = sample.stream().sorted(ranking).toList();

        List<PlaceReview> reviewsToInsert = new ArrayList<>();
        int imageMigrationFailures = 0;
        for (ReviewInput input : ranked) {
            if (reviewsToInsert.size() >= REFRESH_STORAGE_LIMIT) {
                break;
            }
            String targetPath = "places/" + place.getPlaceId() + "/reviews/"
                    + storageSafeSegment(input.getReviewId()) + "/";
            Map<String, String> migratedImages = imageMigrationService.migrateCompressedImages(
                    input.getUserImages(), targetPath);
            List<String> storedImages = input.getUserImages().stream()
                    .map(migratedImages::get)
                    .filter(Objects::nonNull)
                    .filter(url -> storageService.extractObjectKey(url) != null)
                    .distinct()
                    .toList();
            if (storedImages.isEmpty()) {
                imageMigrationFailures++;
                continue;
            }

            String profilePicture = imageMigrationService.migrateCompressedImage(
                    input.getProfilePicture(), targetPath + "profile/");
            if (profilePicture != null && storageService.extractObjectKey(profilePicture) == null) {
                profilePicture = null;
            }

            BigDecimal authenticityScore = scoreCalculator.authenticity(input);
            PlaceReview review = mapInputToReview(
                    input, place.getId(), profilePicture, storedImages);
            review.setAuthenticityScore(authenticityScore);
            review.setAuthenticityLevel(scoreCalculator.authenticityLevel(authenticityScore));
            review.setScoreCalculatedAt(LocalDateTime.now());
            reviewsToInsert.add(review);
        }

        // Defensive idempotency: the worker normally emptied this table before scraping.
        reviewRepository.deleteByPlaceId(place.getId());
        if (!reviewsToInsert.isEmpty()) {
            reviewRepository.insertBatch(reviewsToInsert);
        }

        applyPlaceScore(place, sample);
        place.setLastScrapedAt(LocalDateTime.now());
        place.setUpdatedAt(LocalDateTime.now());
        placeRepository.updateReviewRefreshMetadata(place);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("placeId", place.getId());
        result.put("googlePlaceId", place.getPlaceId());
        result.put("sampled", sample.size());
        result.put("inserted", reviewsToInsert.size());
        result.put("imageMigrationFailures", imageMigrationFailures);
        result.put("lastScrapedAt", place.getLastScrapedAt());
        return result;
    }

    private Place requireActivePlace(UUID placeId, String googlePlaceId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        if (place.getVisibilityStatus() != com.ds.goroute.type.PlaceVisibilityStatus.ACTIVE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Only ACTIVE places can be scraped");
        }
        if (googlePlaceId != null && !googlePlaceId.equals(place.getPlaceId())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "googlePlaceId does not match placeId");
        }
        return place;
    }

    private void clearPlaceReviewScore(Place place) {
        place.setAvgAuthenticityScore(null);
        place.setPlaceOverallScore(null);
        place.setAdjustedRating(null);
        place.setTrustLevel(null);
        place.setIsJcurveDetected(null);
        place.setIsSpikeDetected(null);
        place.setAuthenticLowStarCount(null);
        place.setScoreCalculatedAt(null);
        place.setScoreSampleCount(0);
        place.setScoreSource(null);
    }

    private void applyPlaceScore(Place place, List<ReviewInput> sample) {
        PlaceReviewScoreCalculator.PlaceScoreResult score = scoreCalculator.scoreInputs(
                place.getReviewRating(), Objects.requireNonNullElse(place.getReviewCount(), 0), sample);
        if (score == null) {
            clearPlaceReviewScore(place);
            return;
        }
        place.setAvgAuthenticityScore(score.avgAuthenticityScore());
        place.setPlaceOverallScore(score.placeOverallScore());
        place.setAdjustedRating(score.adjustedRating());
        place.setTrustLevel(score.trustLevel());
        place.setIsJcurveDetected(score.jCurveDetected());
        place.setIsSpikeDetected(score.spikeDetected());
        place.setAuthenticLowStarCount(score.authenticLowStarCount());
        place.setScoreCalculatedAt(LocalDateTime.now());
        place.setScoreSampleCount(score.sampleCount());
        place.setScoreSource("SCRAPED_REVIEWS");
    }

    private void deleteManagedImages(List<PlaceReview> reviews) {
        List<String> urls = new ArrayList<>();
        for (PlaceReview review : reviews) {
            if (storageService.extractObjectKey(review.getProfilePicture()) != null) {
                urls.add(review.getProfilePicture());
            }
            for (String image : parseExistingImages(review.getImages())) {
                if (storageService.extractObjectKey(image) != null) {
                    urls.add(image);
                }
            }
        }
        if (!urls.isEmpty()) {
            storageService.deleteFiles(urls.stream().distinct().toList());
        }
    }

    private String storageSafeSegment(String value) {
        String safe = value == null ? "review" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isBlank() ? "review" : safe;
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

    private boolean isManagedImage(String imageUrl) {
        return storageService.extractObjectKey(imageUrl) != null;
    }

    private String migrateManagedImage(String imageUrl, String targetPath) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (isManagedImage(imageUrl)) {
            return imageUrl;
        }
        String migrated = imageMigrationService.migrateCompressedImage(imageUrl, targetPath);
        return isManagedImage(migrated) ? migrated : null;
    }

    private List<String> migrateManagedImages(List<String> imageUrls, String targetPath) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        List<String> existingManaged = managedImages(imageUrls);
        List<String> external = imageUrls.stream()
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .filter(url -> !isManagedImage(url))
                .distinct()
                .toList();
        if (external.isEmpty()) {
            return existingManaged;
        }
        Map<String, String> migrated = imageMigrationService.migrateCompressedImages(external, targetPath);
        return Stream.concat(
                        existingManaged.stream(),
                        external.stream().map(migrated::get).filter(this::isManagedImage))
                .distinct()
                .toList();
    }

    private List<String> managedImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        return imageUrls.stream()
                .filter(Objects::nonNull)
                .filter(this::isManagedImage)
                .distinct()
                .toList();
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
     * Delete old images from S3 before updating review (selectively)
     */
    private void deleteOldReviewImagesSelectively(PlaceReview review, boolean keepProfile, boolean keepImages) {
        List<String> urlsToDelete = new ArrayList<>();
        
        // Delete old profile picture only if not keeping it
        if (!keepProfile && isManagedImage(review.getProfilePicture())) {
            urlsToDelete.add(review.getProfilePicture());
        }
        
        // Delete old user images only if not keeping them
        if (!keepImages && review.getImages() != null && !review.getImages().equals("[]")) {
            try {
                List<String> imageUrls = JsonUtils.fromJson(review.getImages(), List.class);
                if (imageUrls != null) {
                    for (Object urlObj : imageUrls) {
                        String url = urlObj.toString();
                        if (isManagedImage(url)) {
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
