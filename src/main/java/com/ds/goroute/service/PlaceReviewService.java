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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;

    /**
     * Batch insert reviews from crawler data
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
        int failed = 0;
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

        // Process reviews
        List<PlaceReview> reviewsToInsert = new ArrayList<>();

        for (ReviewInput input : reviewInputs) {
            try {
                UUID placeId = placeIdMap.get(input.getGooglePlaceId());
                if (placeId == null) {
                    continue; // Already counted as failed
                }

                // Check if review already exists
                Optional<PlaceReview> existing = reviewRepository.findByReviewId(input.getReviewId());
                if (existing.isPresent()) {
                    // Update if content changed
                    PlaceReview existingReview = existing.get();
                    if (!existingReview.getContentHash().equals(input.getContentHash())) {
                        updateReviewFromInput(existingReview, input, placeId);
                        reviewRepository.update(existingReview);
                        updated++;
                    } else {
                        skipped++;
                    }
                    continue;
                }

                // Create new review
                PlaceReview review = mapInputToReview(input, placeId);
                reviewsToInsert.add(review);

            } catch (Exception e) {
                log.error("Error processing review {}: {}", input.getReviewId(), e.getMessage());
                errors.add("Error processing review " + input.getReviewId() + ": " + e.getMessage());
                failed++;
            }
        }

        // Batch insert new reviews
        if (!reviewsToInsert.isEmpty()) {
            try {
                reviewRepository.insertBatch(reviewsToInsert);
                inserted += reviewsToInsert.size();
            } catch (Exception e) {
                log.error("Error batch inserting reviews: {}", e.getMessage());
                errors.add("Batch insert failed: " + e.getMessage());
                failed += reviewsToInsert.size();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalInput", totalInput);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("errors", errors);

        return result;
    }

    /**
     * Map ReviewInput to PlaceReview entity
     */
    private PlaceReview mapInputToReview(ReviewInput input, UUID placeId) {
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
                .profilePicture(input.getProfilePicture())
                .isLocalGuide(input.getIsLocalGuide())
                .totalReviews(input.getTotalReviews())
                .totalPhotos(input.getTotalPhotos())
                .rating(input.getRating())
                .description(description)
                .language(mapLanguage(language))
                .reviewDate(parseReviewDate(input.getReviewDate()))
                .images(JsonUtils.toJson(input.getUserImages() != null ? input.getUserImages() : Collections.emptyList()))
                .likes(input.getLikes() != null ? input.getLikes() : 0)
                .contentHash(input.getContentHash())
                .isDeleted(input.getIsDeleted())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update existing review from input
     */
    private void updateReviewFromInput(PlaceReview review, ReviewInput input, UUID placeId) {
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
        review.setProfilePicture(input.getProfilePicture());
        review.setIsLocalGuide(input.getIsLocalGuide());
        review.setTotalReviews(input.getTotalReviews());
        review.setTotalPhotos(input.getTotalPhotos());
        review.setRating(input.getRating());
        review.setDescription(description);
        review.setLanguage(mapLanguage(language));
        review.setReviewDate(parseReviewDate(input.getReviewDate()));
        review.setImages(JsonUtils.toJson(input.getUserImages() != null ? input.getUserImages() : Collections.emptyList()));
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
     */
    private LocalDate parseReviewDate(String dateStr) {
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateStr);
            return offsetDateTime.toLocalDate();
        } catch (Exception e) {
            log.error("Error parsing review date: {}", dateStr);
            return null;  // Return null instead of current date to avoid wrong data
        }
    }
}
