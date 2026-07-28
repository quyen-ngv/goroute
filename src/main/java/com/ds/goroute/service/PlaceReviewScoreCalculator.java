package com.ds.goroute.service;

import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.type.PlaceTrustLevel;
import com.ds.goroute.type.ReviewAuthenticityLevel;
import com.ds.goroute.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PlaceReviewScoreCalculator {

    public BigDecimal authenticity(PlaceReview review) {
        return authenticity(
                review.getDescription(),
                parseImages(review.getImages()),
                review.getTotalReviews(),
                review.getTotalPhotos(),
                review.getIsLocalGuide());
    }

    public BigDecimal authenticity(ReviewInput review) {
        String description = review.getReviewText() == null || review.getReviewText().isEmpty()
                ? null
                : review.getReviewText().values().iterator().next();
        return authenticity(description, review.getUserImages(), review.getTotalReviews(),
                review.getTotalPhotos(), review.getIsLocalGuide());
    }

    public BigDecimal authenticity(String description, List<String> images, Integer totalReviews,
                                   Integer totalPhotos, Boolean localGuide) {
        double hasText = description != null && !description.isEmpty() ? 1.0 : 0.0;
        double textLength = description == null ? 0.0 : Math.min(description.length() / 200.0, 1.0);
        double hasPhotos = images != null && !images.isEmpty() ? 1.0 : 0.0;
        double reviewCredibility = Math.min(Objects.requireNonNullElse(totalReviews, 0) / 50.0, 1.0);
        double photoCredibility = Math.min(Objects.requireNonNullElse(totalPhotos, 0) / 100.0, 1.0);
        double guideCredibility = Boolean.TRUE.equals(localGuide) ? 1.0 : 0.0;
        double reviewerCredibility = (reviewCredibility + photoCredibility + guideCredibility) / 3.0;
        double score = 0.15 * hasText
                + 0.25 * textLength
                + 0.25 * hasPhotos
                + 0.35 * reviewerCredibility;
        return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
    }

    public ReviewAuthenticityLevel authenticityLevel(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(0.80)) >= 0) {
            return ReviewAuthenticityLevel.HIGH;
        }
        if (score.compareTo(BigDecimal.valueOf(0.50)) >= 0) {
            return ReviewAuthenticityLevel.MEDIUM;
        }
        return ReviewAuthenticityLevel.LOW;
    }

    public PlaceScoreResult scoreStoredReviews(BigDecimal googleRating, int googleReviewCount,
                                               List<PlaceReview> reviews) {
        List<ScoredReview> samples = reviews.stream()
                .filter(review -> !Boolean.TRUE.equals(review.getIsDeleted()))
                .map(review -> new ScoredReview(
                        review.getRating(),
                        review.getAuthenticityScore() != null ? review.getAuthenticityScore() : authenticity(review),
                        review.getReviewDate()))
                .toList();
        return scorePlace(googleRating, googleReviewCount, samples);
    }

    public PlaceScoreResult scoreInputs(BigDecimal googleRating, int googleReviewCount, List<ReviewInput> reviews) {
        List<ScoredReview> samples = reviews.stream()
                .map(review -> new ScoredReview(review.getRating(), authenticity(review), parseDate(review.getReviewDate())))
                .toList();
        return scorePlace(googleRating, googleReviewCount, samples);
    }

    public PlaceScoreResult scorePlace(BigDecimal googleRating, int googleReviewCount, List<ScoredReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return null;
        }

        double avgAuth = reviews.stream()
                .map(ScoredReview::authenticityScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        long authenticLowStars = reviews.stream()
                .filter(review -> review.rating() != null && review.rating() <= 2)
                .filter(review -> review.authenticityScore() != null
                        && review.authenticityScore().compareTo(BigDecimal.valueOf(0.5)) >= 0)
                .count();
        double lowStarSignal = authenticLowStars == 0 ? 1.0
                : authenticLowStars == 1 ? 0.7
                : authenticLowStars == 2 ? 0.4 : 0.0;

        int total = reviews.size();
        long fiveStars = reviews.stream().filter(review -> Integer.valueOf(5).equals(review.rating())).count();
        long midStars = reviews.stream()
                .filter(review -> review.rating() != null && review.rating() >= 2 && review.rating() <= 4)
                .count();
        boolean jCurve = (double) fiveStars / total > 0.70 && (double) midStars / total < 0.10;
        double distributionScore = jCurve ? 0.2 : 1.0;

        Map<LocalDate, Long> reviewsPerDay = reviews.stream()
                .map(ScoredReview::reviewDate)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        boolean spike = false;
        if (!reviewsPerDay.isEmpty()) {
            long maxDay = reviewsPerDay.values().stream().max(Long::compareTo).orElse(0L);
            double avgDay = reviewsPerDay.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
            spike = maxDay > avgDay * 5;
        }
        double spikePenalty = spike ? 0.2 : 1.0;

        double overall = 0.50 * avgAuth
                + 0.20 * lowStarSignal
                + 0.20 * distributionScore
                + 0.10 * spikePenalty;
        BigDecimal overallScore = BigDecimal.valueOf(overall).setScale(3, RoundingMode.HALF_UP);
        BigDecimal adjusted = googleReviewCount >= 5 && googleRating != null
                ? googleRating.multiply(overallScore).setScale(2, RoundingMode.HALF_UP)
                : null;
        return new PlaceScoreResult(
                BigDecimal.valueOf(avgAuth).setScale(3, RoundingMode.HALF_UP),
                overallScore,
                adjusted,
                getTrustLevel(overall),
                jCurve,
                spike,
                Math.toIntExact(authenticLowStars),
                reviews.size());
    }

    public List<ReviewInput> selectForStorage(List<ReviewInput> reviews, int limit, int lowStarQuota) {
        Comparator<ReviewInput> ranking = Comparator
                .comparing((ReviewInput review) -> authenticity(review), Comparator.reverseOrder())
                .thenComparing(review -> Objects.requireNonNullElse(review.getLikes(), 0), Comparator.reverseOrder())
                .thenComparing(review -> parseDate(review.getReviewDate()), Comparator.nullsLast(Comparator.reverseOrder()));
        List<ReviewInput> photoReviews = reviews.stream()
                .filter(review -> review.getUserImages() != null && !review.getUserImages().isEmpty())
                .sorted(ranking)
                .toList();

        LinkedHashMap<String, ReviewInput> selected = new LinkedHashMap<>();
        photoReviews.stream()
                .filter(review -> review.getRating() != null && review.getRating() <= 2)
                .filter(review -> authenticity(review).compareTo(BigDecimal.valueOf(0.5)) >= 0)
                .limit(Math.max(0, Math.min(lowStarQuota, limit)))
                .forEach(review -> selected.put(review.getReviewId(), review));
        for (ReviewInput review : photoReviews) {
            if (selected.size() >= limit) {
                break;
            }
            selected.putIfAbsent(review.getReviewId(), review);
        }
        return new ArrayList<>(selected.values());
    }

    public LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value).toLocalDate();
            } catch (Exception ignoredAgain) {
                try {
                    return LocalDate.parse(value);
                } catch (Exception ignoredFinally) {
                    return null;
                }
            }
        }
    }

    private List<String> parseImages(String value) {
        try {
            List<String> images = JsonUtils.fromJson(value, List.class);
            return images == null ? List.of() : images;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private PlaceTrustLevel getTrustLevel(double score) {
        if (score >= 0.80) return PlaceTrustLevel.TRUSTED;
        if (score >= 0.55) return PlaceTrustLevel.MODERATE;
        if (score >= 0.30) return PlaceTrustLevel.CAUTION;
        return PlaceTrustLevel.SUSPICIOUS;
    }

    public record ScoredReview(Integer rating, BigDecimal authenticityScore, LocalDate reviewDate) {}

    public record PlaceScoreResult(
            BigDecimal avgAuthenticityScore,
            BigDecimal placeOverallScore,
            BigDecimal adjustedRating,
            PlaceTrustLevel trustLevel,
            boolean jCurveDetected,
            boolean spikeDetected,
            int authenticLowStarCount,
            int sampleCount) {}
}
