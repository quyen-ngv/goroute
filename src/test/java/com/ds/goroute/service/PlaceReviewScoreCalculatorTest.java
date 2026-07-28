package com.ds.goroute.service;

import com.ds.goroute.dto.request.ReviewInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceReviewScoreCalculatorTest {
    private final PlaceReviewScoreCalculator calculator = new PlaceReviewScoreCalculator();

    @Test
    void calculatesAuthenticityWithTheProductionWeights() {
        ReviewInput review = review("full", 5, true, true, 50, 100, 200);

        assertThat(calculator.authenticity(review)).isEqualByComparingTo("1.000");
    }

    @Test
    void scoresTheFullSampleAndProducesAdjustedRating() {
        List<ReviewInput> reviews = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            reviews.add(review("good-" + index, index < 2 ? 2 : 5, true, true, 50, 100, 200));
        }

        PlaceReviewScoreCalculator.PlaceScoreResult result = calculator.scoreInputs(
                BigDecimal.valueOf(4.5), 500, reviews);

        assertThat(result.sampleCount()).isEqualTo(20);
        assertThat(result.authenticLowStarCount()).isEqualTo(2);
        assertThat(result.avgAuthenticityScore()).isEqualByComparingTo("1.000");
        assertThat(result.adjustedRating()).isGreaterThan(BigDecimal.valueOf(3));
    }

    @Test
    void selectsPhotoReviewsByLegitimacyAndReservesLowStarSignals() {
        List<ReviewInput> reviews = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            reviews.add(review("five-" + index, 5, true, true, 50, 100, 200));
        }
        for (int index = 0; index < 6; index++) {
            reviews.add(review("low-" + index, 1, true, true, 50, 100, 200));
        }
        reviews.add(review("no-photo", 1, false, true, 50, 100, 200));

        List<ReviewInput> selected = calculator.selectForStorage(reviews, 20, 4);

        assertThat(selected).hasSize(20).allMatch(review -> !review.getUserImages().isEmpty());
        assertThat(selected.stream().filter(review -> review.getRating() <= 2)).hasSize(4);
        assertThat(selected).extracting(ReviewInput::getReviewId).doesNotContain("no-photo");
    }

    @Test
    void neverInjectsLowAuthenticityReviewsJustToFillTheQuota() {
        List<ReviewInput> reviews = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            reviews.add(review("good-" + index, 5, true, true, 50, 100, 200));
        }
        reviews.add(review("weak-low", 1, true, false, 0, 0, 0));

        List<ReviewInput> selected = calculator.selectForStorage(reviews, 20, 4);

        assertThat(selected).extracting(ReviewInput::getReviewId).doesNotContain("weak-low");
    }

    private ReviewInput review(String id, int rating, boolean photo, boolean guide,
                               int totalReviews, int totalPhotos, int textLength) {
        return ReviewInput.builder()
                .reviewId(id)
                .googlePlaceId("place-1")
                .authorName("Reviewer " + id)
                .isLocalGuide(guide)
                .totalReviews(totalReviews)
                .totalPhotos(totalPhotos)
                .rating(rating)
                .reviewText(Map.of("vi", "x".repeat(textLength)))
                .reviewDate("2026-07-01")
                .userImages(photo ? List.of("https://images.example/" + id + ".jpg") : List.of())
                .likes(1)
                .contentHash("hash-" + id)
                .isDeleted(false)
                .build();
    }
}
