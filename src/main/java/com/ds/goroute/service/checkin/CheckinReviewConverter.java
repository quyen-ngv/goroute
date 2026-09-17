package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a score somebody left on a check-in into their one review of a place.
 *
 * <p>Shared by the two operator paths that attach a place to check-ins that already exist:
 * promoting or merging a cluster (CHK-12) and moving a single check-in onto the right
 * catalogue row. Both mean the same thing -- "this visit turns out to have a place" -- so
 * they must produce the same review, or the same rating would count differently depending
 * on which screen the operator happened to use.
 *
 * <p>The review is never marked location-verified: proximity can only be judged against a
 * place at the moment of the visit, and there was no place to judge against then.
 */
@Component
@RequiredArgsConstructor
public class CheckinReviewConverter {

    private final UserReviewRepository reviewRepository;

    /** @return the id of the created or updated review */
    public UUID convert(UserCheckin checkin, UUID placeId, List<String> photoUrls) {
        Optional<UserReview> existing = reviewRepository.findByUserAndPlace(checkin.getUserId(), placeId);
        LocalDateTime now = LocalDateTime.now();
        String reviewPhotos = JsonUtils.toJson(photoUrls);

        if (existing.isPresent()) {
            UserReview review = existing.get();
            review.setOverallRating(checkin.getOverallRating());
            review.setFoodRating(checkin.getFoodRating());
            review.setPriceRating(checkin.getPriceRating());
            review.setAmbianceRating(checkin.getAmbianceRating());
            review.setServiceRating(checkin.getServiceRating());
            review.setText(checkin.getCaption());
            review.setPhotos(reviewPhotos);
            review.setCheckinLat(checkin.getLatitude());
            review.setCheckinLng(checkin.getLongitude());
            review.setCheckinAccuracy(checkin.getAccuracyMeters());
            review.setLocationVerified(Boolean.FALSE);
            review.setUpdatedAt(now);
            reviewRepository.update(review);
            return review.getId();
        }

        UserReview review = UserReview.builder()
                .id(UUID.randomUUID())
                .userId(checkin.getUserId())
                .placeId(placeId)
                .overallRating(checkin.getOverallRating())
                .foodRating(checkin.getFoodRating())
                .priceRating(checkin.getPriceRating())
                .ambianceRating(checkin.getAmbianceRating())
                .serviceRating(checkin.getServiceRating())
                .text(checkin.getCaption())
                .photos(reviewPhotos)
                .checkinLat(checkin.getLatitude())
                .checkinLng(checkin.getLongitude())
                .checkinAccuracy(checkin.getAccuracyMeters())
                .locationVerified(Boolean.FALSE)
                .weight(BigDecimal.ONE)
                .helpfulVotes(0)
                .unhelpfulVotes(0)
                .createdAt(checkin.getCreatedAt())
                .updatedAt(now)
                .build();
        reviewRepository.save(review);
        return review.getId();
    }
}
