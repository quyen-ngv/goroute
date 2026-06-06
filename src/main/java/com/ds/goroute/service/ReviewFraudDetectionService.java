package com.ds.goroute.service;

import com.ds.goroute.entity.ReviewFlag;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.UserReviewProfile;
import com.ds.goroute.repository.ReviewFlagRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.type.FlagSeverity;
import com.ds.goroute.type.ReviewFlagType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewFraudDetectionService {
    
    private final UserReviewRepository reviewRepository;
    private final UserReviewProfileRepository profileRepository;
    private final ReviewFlagRepository flagRepository;
    
    /**
     * Detect and flag suspicious reviews
     */
    public List<ReviewFlag> detectAndFlagReview(UserReview review) {
        List<ReviewFlag> flags = new ArrayList<>();
        
        // 1. Velocity check
        if (isHighVelocity(review.getUserId())) {
            flags.add(createFlag(review.getId(), ReviewFlagType.HIGH_VELOCITY, 
                    FlagSeverity.MEDIUM, "User created too many reviews in 24 hours"));
        }
        
        // 2. Suspicious new account
        if (isSuspiciousNewAccount(review)) {
            flags.add(createFlag(review.getId(), ReviewFlagType.SUSPICIOUS_NEW_ACCOUNT,
                    FlagSeverity.HIGH, "New account with extreme rating"));
        }
        
        // 3. Duplicate text detection
        if (hasDuplicateText(review)) {
            flags.add(createFlag(review.getId(), ReviewFlagType.DUPLICATE_TEXT,
                    FlagSeverity.HIGH, "Review text similar to existing reviews"));
        }
        
        // Save flags
        flags.forEach(flagRepository::save);
        
        return flags;
    }
    
    /**
     * Check if user is creating reviews too quickly
     */
    private boolean isHighVelocity(UUID userId) {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        int recentReviews = reviewRepository.countByUserInTimeRange(
                userId, last24Hours, LocalDateTime.now());
        
        return recentReviews > 5;
    }
    
    /**
     * Check if new account with extreme rating
     */
    private boolean isSuspiciousNewAccount(UserReview review) {
        UserReviewProfile profile = profileRepository.findByUserId(review.getUserId())
                .orElse(null);
        
        if (profile == null) {
            return false;
        }
        
        // Account less than 7 days old with extreme rating (1 or 5)
        boolean isNewAccount = profile.getReviewCount() < 3;
        boolean isExtremeRating = review.getOverallRating() == 1 || review.getOverallRating() == 5;
        
        return isNewAccount && isExtremeRating;
    }
    
    /**
     * Check for duplicate or very similar text
     */
    private boolean hasDuplicateText(UserReview review) {
        if (review.getText() == null || review.getText().length() < 20) {
            return false;
        }
        
        // Get other reviews for the same target.
        List<UserReview> otherReviews = review.getPlaceId() != null
                ? reviewRepository.findByPlaceId(review.getPlaceId(), 100, 0)
                : reviewRepository.findByActivityBookingId(review.getActivityBookingId(), 100, 0);
        
        for (UserReview other : otherReviews) {
            if (other.getId().equals(review.getId())) {
                continue;
            }
            
            if (other.getText() != null && calculateSimilarity(review.getText(), other.getText()) > 0.85) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Simple text similarity using Jaccard index
     */
    private double calculateSimilarity(String text1, String text2) {
        String[] words1 = text1.toLowerCase().split("\\s+");
        String[] words2 = text2.toLowerCase().split("\\s+");
        
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(words1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(words2));
        
        java.util.Set<String> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);
        
        java.util.Set<String> union = new java.util.HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Create a review flag
     */
    private ReviewFlag createFlag(UUID reviewId, ReviewFlagType type, FlagSeverity severity, String reason) {
        return ReviewFlag.builder()
                .id(UUID.randomUUID())
                .reviewId(reviewId)
                .flagType(type)
                .severity(severity)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
