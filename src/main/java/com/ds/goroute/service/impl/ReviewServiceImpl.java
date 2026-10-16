package com.ds.goroute.service.impl;

import com.ds.goroute.dto.FeedCursor;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.ReviewEligibilityResponse;
import com.ds.goroute.dto.response.ReviewPartnerResponse;
import com.ds.goroute.dto.response.ReviewScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.entity.*;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedPostMapper;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewFraudDetectionService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.SavedPostType;
import com.ds.goroute.type.UserTier;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final StarService starService;

    private final UserReviewRepository reviewRepository;
    private final UserReviewProfileRepository profileRepository;
    private final PlaceScoreRepository scoreRepository;
    private final ReviewHelpfulVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final UserCheckinRepository checkinRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final HotelMarketplaceRepository hotelMarketplaceRepository;
    private final ActivityCommerceRepository activityCommerceRepository;
    private final MediaAssetRepository mediaAssetRepository;

    private static final String USER_REVIEW_ENTITY_TYPE = "USER_REVIEW";

    /**
     * Ceiling for every review list. The controllers validate it too; this is the backstop for
     * callers that do not (the public profile endpoint), so no request reads an unbounded page.
     */
    static final int MAX_PAGE_SIZE = 100;

    private final ReviewScoringService scoringService;
    private final ReviewFraudDetectionService fraudDetectionService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final SocialNotificationService socialNotificationService;
    private final ContentModerationService contentModerationService;
    private final ContentCommentRepository contentCommentRepository;
    private final CheckinVerifier checkinVerifier;
    private final SavedPostMapper savedPostMapper;

    /**
     * Create a new review
     */
    @Override
    @Transactional
    public UserReviewResponse createReview(UUID userId, CreateReviewRequest request) {
        // A booking-linked review takes its target from the booking; the client-supplied target is ignored.
        Optional<BookingLink> bookingLink = resolveBookingLink(userId, request.getHotelBookingId(), request.getActivityOrderId());
        UUID placeId = request.getPlaceId();
        UUID activityBookingId = request.getActivityBookingId();
        if (bookingLink.isPresent()) {
            placeId = bookingLink.get().placeId();
            activityBookingId = bookingLink.get().activityBookingId();
        }
        validateReviewTarget(placeId, activityBookingId);

        Optional<UserReview> existingReview = placeId != null
                ? reviewRepository.findByUserAndPlace(userId, placeId)
                : reviewRepository.findByUserAndActivityBooking(userId, activityBookingId);
        if (existingReview.isPresent()) {
            log.warn("User {} already reviewed target placeId={}, activityBookingId={}. Existing review ID: {}",
                userId, placeId, activityBookingId, existingReview.get().getId());
            throw new BusinessException(ErrorConstant.REVIEW_ALREADY_EXISTS,
                "You have already reviewed this item. Please update your existing review instead.");
        }

        boolean locationVerified = false;
        if (placeId != null) {
            var place = placeRepository.findById(placeId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));
            locationVerified = isVerifiedLocation(request, place);
        } else {
            activityBookingRepository.findById(activityBookingId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));
        }

        // Create review
        UserReview review = UserReview.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(placeId)
                .activityBookingId(activityBookingId)
                .hotelBookingId(bookingLink.map(BookingLink::hotelBookingId).orElse(null))
                .activityOrderId(bookingLink.map(BookingLink::activityOrderId).orElse(null))
                .tripId(null)
                .checkinLat(request.getCheckinLat())
                .checkinLng(request.getCheckinLng())
                .checkinAccuracy(request.getCheckinAccuracy())
                .locationVerified(locationVerified)
                .overallRating(request.getOverallRating())
                .foodRating(request.getFoodRating())
                .priceRating(request.getPriceRating())
                .ambianceRating(request.getAmbianceRating())
                .serviceRating(request.getServiceRating())
                .locationRating(request.getLocationRating())
                .cleanlinessRating(request.getCleanlinessRating())
                .facilitiesRating(request.getFacilitiesRating())
                .text(request.getText())
                .photos(request.getPhotos() != null ? JsonUtils.toJson(request.getPhotos()) : null)
                .weight(BigDecimal.ONE)
                .helpfulVotes(0)
                .unhelpfulVotes(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            reviewRepository.save(review);
        } catch (DataIntegrityViolationException ex) {
            // Unique indexes on (user_id, place_id), hotel_booking_id and activity_order_id: a concurrent
            // submit for the same target or booking lost the race.
            log.warn("Duplicate review rejected for user {} placeId={} activityBookingId={} hotelBookingId={} activityOrderId={}",
                    userId, placeId, activityBookingId, review.getHotelBookingId(), review.getActivityOrderId());
            throw new BusinessException(ErrorConstant.REVIEW_ALREADY_EXISTS,
                    "You have already reviewed this item. Please update your existing review instead.");
        }

        // Fraud detection
        fraudDetectionService.detectAndFlagReview(review);

        // Update user profile
        profileRepository.incrementReviewCount(userId);
        scoringService.updateUserTier(userId);

        if (placeId != null) {
            scoringService.recalculatePlaceScores(placeId);
        }

        return mapToResponse(review, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getEligibility(UUID userId, UUID hotelBookingId, UUID activityOrderId) {
        validateBookingSelector(hotelBookingId, activityOrderId);
        if (hotelBookingId == null && activityOrderId == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "hotelBookingId or activityOrderId is required");
        }
        return evaluateEligibility(userId, hotelBookingId, activityOrderId);
    }

    /** Review target plus the booking ids that make it a verified stay/visit. */
    private record BookingLink(UUID hotelBookingId, UUID activityOrderId, UUID placeId, UUID activityBookingId) {}

    private Optional<BookingLink> resolveBookingLink(UUID userId, UUID hotelBookingId, UUID activityOrderId) {
        validateBookingSelector(hotelBookingId, activityOrderId);
        if (hotelBookingId == null && activityOrderId == null) {
            return Optional.empty();
        }
        ReviewEligibilityResponse eligibility = evaluateEligibility(userId, hotelBookingId, activityOrderId);
        switch (eligibility.getReason()) {
            case NOT_FOUND -> throw new BusinessException(ErrorConstant.NOT_FOUND,
                    hotelBookingId != null ? "Hotel booking not found" : "Activity order not found");
            case NOT_OWNER -> throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "You can only review your own bookings");
            case NOT_COMPLETED -> throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "You can review after your stay/visit");
            case ALREADY_REVIEWED -> throw new BusinessException(ErrorConstant.REVIEW_ALREADY_EXISTS,
                    "You have already reviewed this booking");
            case OK -> { }
        }
        return Optional.of(new BookingLink(hotelBookingId, activityOrderId,
                eligibility.getPlaceId(), eligibility.getActivityBookingId()));
    }

    private void validateBookingSelector(UUID hotelBookingId, UUID activityOrderId) {
        if (hotelBookingId != null && activityOrderId != null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only one of hotelBookingId or activityOrderId may be set");
        }
    }

    /**
     * Eligibility rules, shared by the pre-check endpoint and the create path so they can never disagree:
     * the booking exists, belongs to the caller, has reached CHECKED_IN/COMPLETED, and neither this booking
     * nor the derived target (the hotel's place / the activity product) has been reviewed by the caller yet.
     * The per-target check is kept because {@code UNIQUE(user_id, place_id)} still applies to a repeat stay;
     * the app is expected to offer "update your review" through {@code existingReviewId}.
     */
    private ReviewEligibilityResponse evaluateEligibility(UUID userId, UUID hotelBookingId, UUID activityOrderId) {
        if (hotelBookingId != null) {
            HotelBooking booking = hotelMarketplaceRepository.findBooking(hotelBookingId).orElse(null);
            if (booking == null) {
                return notEligible(ReviewEligibilityResponse.Reason.NOT_FOUND);
            }
            if (!userId.equals(booking.getUserId())) {
                return notEligible(ReviewEligibilityResponse.Reason.NOT_OWNER);
            }
            if (!isReviewableStatus(booking.getBookingStatus())) {
                return notEligible(ReviewEligibilityResponse.Reason.NOT_COMPLETED);
            }
            UUID placeId = hotelMarketplaceRepository.findHotel(booking.getHotelId())
                    .map(HotelProfile::getPlaceId).orElse(null);
            if (placeId == null) {
                return notEligible(ReviewEligibilityResponse.Reason.NOT_FOUND);
            }
            Optional<UserReview> existing = reviewRepository.findByHotelBookingId(hotelBookingId)
                    .or(() -> reviewRepository.findByUserAndPlace(userId, placeId));
            return ReviewEligibilityResponse.builder()
                    .eligible(existing.isEmpty())
                    .reason(existing.isEmpty() ? ReviewEligibilityResponse.Reason.OK
                            : ReviewEligibilityResponse.Reason.ALREADY_REVIEWED)
                    .existingReviewId(existing.map(UserReview::getId).orElse(null))
                    .placeId(placeId)
                    .build();
        }

        ActivityOrder order = activityCommerceRepository.findOrder(activityOrderId).orElse(null);
        if (order == null) {
            return notEligible(ReviewEligibilityResponse.Reason.NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            return notEligible(ReviewEligibilityResponse.Reason.NOT_OWNER);
        }
        if (!isReviewableStatus(order.getOrderStatus())) {
            return notEligible(ReviewEligibilityResponse.Reason.NOT_COMPLETED);
        }
        UUID activityBookingId = order.getActivityBookingId();
        if (activityBookingId == null) {
            return notEligible(ReviewEligibilityResponse.Reason.NOT_FOUND);
        }
        Optional<UserReview> existing = reviewRepository.findByActivityOrderId(activityOrderId)
                .or(() -> reviewRepository.findByUserAndActivityBooking(userId, activityBookingId));
        return ReviewEligibilityResponse.builder()
                .eligible(existing.isEmpty())
                .reason(existing.isEmpty() ? ReviewEligibilityResponse.Reason.OK
                        : ReviewEligibilityResponse.Reason.ALREADY_REVIEWED)
                .existingReviewId(existing.map(UserReview::getId).orElse(null))
                .activityBookingId(activityBookingId)
                .build();
    }

    private static boolean isReviewableStatus(String status) {
        return MarketplaceBookingStatus.COMPLETED.name().equals(status)
                || MarketplaceBookingStatus.CHECKED_IN.name().equals(status);
    }

    private static ReviewEligibilityResponse notEligible(ReviewEligibilityResponse.Reason reason) {
        return ReviewEligibilityResponse.builder().eligible(false).reason(reason).build();
    }

    /**
     * A place review may be submitted without a verified check-in. Location
     * only controls the trust badge/reward eligibility, not whether the
     * review can be created.
     */
    boolean isVerifiedLocation(CreateReviewRequest request, Place place) {
        if (request.getCheckinLat() == null || request.getCheckinLng() == null || request.getCheckinAccuracy() == null) {
            return false;
        }
        // Same accuracy limit, radius and drawn area as a check-in at this place, so the
        // CHECKIN.* settings an operator edits govern both. No ward fallback: a review is
        // either at the place or not verified.
        VerificationTarget target = new VerificationTarget(place.getLatitude(), place.getLongitude(),
                checkinVerifier.effectiveRadius(place), false, place.getId());
        CheckinVerifier.Assessment assessment = checkinVerifier.assess(target,
                request.getCheckinLat(), request.getCheckinLng(), request.getCheckinAccuracy());
        return Boolean.TRUE.equals(assessment.accuracyAcceptable())
                && Boolean.TRUE.equals(assessment.withinPlaceArea());
    }

    /**
     * Update existing review
     */
    @Override
    @Transactional
    public UserReviewResponse updateReview(UUID userId, UUID reviewId, UpdateReviewRequest request) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        // Check ownership
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only edit your own reviews");
        }

        // Update fields
        if (request.getOverallRating() != null) {
            review.setOverallRating(request.getOverallRating());
        }
        if (request.getFoodRating() != null) {
            review.setFoodRating(request.getFoodRating());
        }
        if (request.getPriceRating() != null) {
            review.setPriceRating(request.getPriceRating());
        }
        if (request.getAmbianceRating() != null) {
            review.setAmbianceRating(request.getAmbianceRating());
        }
        if (request.getServiceRating() != null) {
            review.setServiceRating(request.getServiceRating());
        }
        if (request.getLocationRating() != null) {
            review.setLocationRating(request.getLocationRating());
        }
        if (request.getCleanlinessRating() != null) {
            review.setCleanlinessRating(request.getCleanlinessRating());
        }
        if (request.getFacilitiesRating() != null) {
            review.setFacilitiesRating(request.getFacilitiesRating());
        }
        if (request.getText() != null) {
            review.setText(request.getText());
        }
        if (request.getPhotos() != null) {
            review.setPhotos(JsonUtils.toJson(request.getPhotos()));
        }

        review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.update(review);

        if (review.getPlaceId() != null) {
            scoringService.recalculatePlaceScores(review.getPlaceId());
        }

        return mapToResponse(review, userId);
    }

    /**
     * Delete review
     */
    @Override
    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only delete your own reviews");
        }

        UUID placeId = review.getPlaceId();

        imageStorageCleanupService.deleteImagesForEntityRecord("USER_REVIEW", reviewId);
        // Check-ins outlive the review they wrote. Cut the link first so no visit is left
        // pointing at a review that is about to stop existing.
        checkinRepository.detachReview(reviewId);
        reviewRepository.delete(reviewId);
        profileRepository.decrementReviewCount(userId);
        scoringService.updateUserTier(userId);
        if (placeId != null) {
            scoringService.recalculatePlaceScores(placeId);
        }
    }

    /**
     * Get reviews for a place
     */
    @Override
    @Transactional(readOnly = true)
    public UserReviewResponse getReview(UUID reviewId, UUID currentUserId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));
        if (contentModerationService.isTakenDown(ModeratedContentType.REVIEW, reviewId)) {
            throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
        }
        return mapToResponse(review, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserReviewResponse> findVisibleByIds(UUID currentUserId, Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(reviewIds);
        Set<UUID> takenDown = contentModerationService.takenDownIds(ModeratedContentType.REVIEW, ids);
        List<UserReview> visible = reviewRepository.findByIds(ids).stream()
                .filter(review -> !takenDown.contains(review.getId()))
                .toList();
        return mapToResponses(visible, currentUserId).stream()
                .collect(Collectors.toMap(UserReviewResponse::getId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getPlaceReviews(UUID placeId, UUID currentUserId, int page, int size) {
        List<UserReview> reviews = reviewRepository.findByPlaceId(placeId, boundedSize(size), offset(page, size));

        return mapToResponses(reviews, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getActivityBookingReviews(UUID activityBookingId, UUID currentUserId, int page, int size) {
        activityBookingRepository.findById(activityBookingId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));
        List<UserReview> reviews = reviewRepository.findByActivityBookingId(
                activityBookingId, boundedSize(size), offset(page, size));

        return mapToResponses(reviews, currentUserId);
    }

    /**
     * Get user's reviews
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getUserReviews(UUID userId, int page, int size) {
        List<UserReview> reviews = reviewRepository.findByUserId(userId, boundedSize(size), offset(page, size));

        return mapToResponses(reviews, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getUserReviewsForProfile(UUID targetUserId, UUID viewerId, int page, int size) {
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));
        List<UserReview> reviews = reviewRepository.findByUserId(targetUserId, boundedSize(size), offset(page, size));

        return mapToResponses(reviews, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getFeedReviews(UUID currentUserId, FeedGeoFilter geo, int page, int size, String randomSeed) {
        return getFeedReviews(currentUserId, geo, page, size, randomSeed, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReviewResponse> getFeedReviews(UUID currentUserId, FeedGeoFilter geo, int page, int size,
                                                   String randomSeed, String cursor) {
        boolean shuffled = randomSeed != null && !randomSeed.isEmpty();
        FeedCursor after = shuffled ? null : FeedCursor.decode(cursor);
        List<UserReview> reviews = reviewRepository.findFeedReviews(currentUserId, geo, after,
                boundedSize(size), after != null ? 0 : offset(page, size), randomSeed);

        List<UserReviewResponse> responses = mapToResponses(reviews, currentUserId);
        if (!shuffled) {
            // Same order as the rows, so each review carries the key the next page resumes after.
            for (int index = 0; index < responses.size(); index++) {
                UserReview review = reviews.get(index);
                responses.get(index).setFeedCursor(FeedCursor.encodeAfter(
                        review.getHasPhoto(), review.getCreatedAt(), review.getId()));
            }
        }
        return responses;
    }

    private static int boundedSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private static int offset(int page, int size) {
        return Math.max(0, page) * boundedSize(size);
    }

    /**
     * Vote review as helpful
     */
    @Override
    @Transactional
    public UserReviewResponse voteHelpful(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        ReviewHelpfulVote existingVote = voteRepository.findByReviewIdAndUserId(reviewId, userId);

        boolean isHelpfulNow = true;
        if (existingVote != null) {
            if (existingVote.isHelpful()) {
                // Already voted helpful, remove vote
                voteRepository.delete(reviewId, userId);
                isHelpfulNow = false;
            } else {
                // Was unhelpful, change to helpful
                existingVote.setHelpful(true);
                voteRepository.update(existingVote);
            }
        } else {
            // New helpful vote
            ReviewHelpfulVote vote = ReviewHelpfulVote.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .isHelpful(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            voteRepository.save(vote);
        }

        UserReviewResponse response = syncVoteCounts(review, userId);
        if (isHelpfulNow) {
            socialNotificationService.notifyLike(
                    review.getUserId(), userId, ModeratedContentType.REVIEW.name(), reviewId);
        }
        return response;
    }

    @Override
    @Transactional
    public UserReviewResponse voteUnhelpful(UUID userId, UUID reviewId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND, "Review not found"));

        ReviewHelpfulVote existingVote = voteRepository.findByReviewIdAndUserId(reviewId, userId);

        if (existingVote != null) {
            if (!existingVote.isHelpful()) {
                // Already voted unhelpful, remove vote
                voteRepository.delete(reviewId, userId);
            } else {
                // Was helpful, change to unhelpful
                existingVote.setHelpful(false);
                voteRepository.update(existingVote);
            }
        } else {
            // New unhelpful vote
            ReviewHelpfulVote vote = ReviewHelpfulVote.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .isHelpful(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            voteRepository.save(vote);
        }

        return syncVoteCounts(review, userId);
    }

    private UserReviewResponse syncVoteCounts(UserReview review, UUID currentUserId) {
        int helpfulCount = voteRepository.countByReviewIdAndIsHelpful(review.getId(), true);
        int unhelpfulCount = voteRepository.countByReviewIdAndIsHelpful(review.getId(), false);
        review.setHelpfulVotes(helpfulCount);
        review.setUnhelpfulVotes(unhelpfulCount);
        reviewRepository.updateVoteCounts(review);

        if (helpfulCount >= 5) {
            starService.grant(review.getUserId(), 1, "REVIEW_LIKES",
                    "review_likes:" + review.getId(), "Your review reached 5 helpful votes");
        }

        // Update reviewer's profile
        scoringService.updateUserTier(review.getUserId());

        return mapToResponse(review, currentUserId);
    }

    /**
     * Get place score
     */
    @Override
    @Transactional(readOnly = true)
    public PlaceScoreResponse getPlaceScore(UUID placeId) {
        PlaceScore score = scoreRepository.findByPlaceId(placeId).orElse(null);

        if (score == null) {
            return PlaceScoreResponse.builder()
                    .placeId(placeId)
                    .reviewCount(0)
                    .displayLabel("Not enough reviews")
                    .useGoogleScore(true)
                    .build();
        }

        boolean useGoogle = score.getReviewCount() < 10;

        return PlaceScoreResponse.builder()
                .placeId(placeId)
                .tripmindScore(score.getTripmindScore())
                .googleScore(score.getGoogleScore())
                .reviewCount(score.getReviewCount())
                .foodScore(score.getFoodScore())
                .priceScore(score.getPriceScore())
                .ambianceScore(score.getAmbianceScore())
                .serviceScore(score.getServiceScore())
                .nationalityBreakdown(score.getNationalityBreakdown() != null ?
                        JsonUtils.fromJson(score.getNationalityBreakdown(), Map.class) : null)
                .displayScore(useGoogle && score.getGoogleScore() != null ?
                        score.getGoogleScore().toString() :
                        (score.getTripmindScore() != null ? score.getTripmindScore().toString() : "N/A"))
                .displayLabel(score.getReviewCount() < 10 ? "Preliminary" :
                        "TripMind Score")
                .useGoogleScore(useGoogle)
                .lastCalculatedAt(score.getLastCalculatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewScoreResponse getActivityBookingScore(UUID activityBookingId) {
        activityBookingRepository.findById(activityBookingId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));

        List<UserReview> reviews = reviewRepository.findByActivityBookingId(activityBookingId, 1000, 0);
        int reviewCount = reviews.size();
        BigDecimal score = averageRating(reviews, UserReview::getOverallRating);
        BigDecimal foodScore = averageRating(reviews, UserReview::getFoodRating);
        BigDecimal priceScore = averageRating(reviews, UserReview::getPriceRating);
        BigDecimal ambianceScore = averageRating(reviews, UserReview::getAmbianceRating);
        BigDecimal serviceScore = averageRating(reviews, UserReview::getServiceRating);

        return ReviewScoreResponse.builder()
                .targetId(activityBookingId)
                .targetType("ACTIVITY_BOOKING")
                .score(score)
                .reviewCount(reviewCount)
                .foodScore(foodScore)
                .priceScore(priceScore)
                .ambianceScore(ambianceScore)
                .serviceScore(serviceScore)
                .displayScore(score != null ? score.toString() : "N/A")
                .displayLabel(reviewCount == 0 ? "Not enough reviews" : "TripMind Score")
                .lastCalculatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get user review profile
     */
    @Override
    @Transactional(readOnly = true)
    public UserReviewProfileResponse getUserProfile(UUID userId) {
        UserReviewProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));

        return UserReviewProfileResponse.builder()
                .userId(userId)
                .tier(profile.getTier())
                .trustScore(profile.getTrustScore())
                .reviewCount(profile.getReviewCount())
                .avgReviewLength(profile.getAvgReviewLength())
                .helpfulVotesReceived(profile.getHelpfulVotesReceived())
                .verifiedTripsCount(profile.getVerifiedTripsCount())
                .tierDisplay(getTierDisplay(profile.getTier()))
                .tierDescription(getTierDescription(profile.getTier()))
                .build();
    }

    /**
     * Every list path goes through here: each kind of related row is loaded once for the whole
     * page (authors, their profiles, places - a place's own review list loads that place once -
     * activity products, the viewer's votes, photos, comment counts), so a page costs the same
     * handful of queries whatever its size.
     */
    private List<UserReviewResponse> mapToResponses(List<UserReview> reviews, UUID currentUserId) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        List<UUID> reviewIds = reviews.stream().map(UserReview::getId).toList();
        Map<UUID, List<MediaAsset>> photosByReview = mediaAssetRepository
                .findByEntityIds(USER_REVIEW_ENTITY_TYPE, reviewIds)
                .stream()
                .collect(Collectors.groupingBy(MediaAsset::getEntityId));
        // One grouped query for the page rather than the card's own count request per row.
        Map<UUID, Integer> commentCounts = contentCommentRepository.countActiveByTargets(
                ModeratedContentType.REVIEW, reviewIds);

        Set<UUID> authorIds = reviews.stream().map(UserReview::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, User> users = userRepository.findByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<UUID, UserReviewProfile> profiles = profileRepository.findByUserIds(authorIds).stream()
                .collect(Collectors.toMap(UserReviewProfile::getUserId, Function.identity(),
                        (first, second) -> first));
        List<UUID> placeIds = reviews.stream().map(UserReview::getPlaceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, Place> places = placeIds.isEmpty() ? Map.of()
                : placeRepository.findByIds(placeIds).stream()
                        .collect(Collectors.toMap(Place::getId, Function.identity(), (first, second) -> first));
        List<UUID> bookingIds = reviews.stream().map(UserReview::getActivityBookingId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, ActivityBooking> bookings = bookingIds.isEmpty() ? Map.of()
                : activityBookingRepository.findByIds(bookingIds).stream()
                        .collect(Collectors.toMap(ActivityBooking::getId, Function.identity(),
                                (first, second) -> first));
        Map<UUID, Boolean> votes = currentUserId == null ? Map.of()
                : voteRepository.findByReviewIdsAndUserId(reviewIds, currentUserId).stream()
                        .collect(Collectors.toMap(ReviewHelpfulVote::getReviewId, ReviewHelpfulVote::isHelpful,
                                (first, second) -> first));

        Set<UUID> savedIds = currentUserId == null ? Set.of()
                : new HashSet<>(savedPostMapper.findSavedIds(currentUserId, SavedPostType.REVIEW, reviewIds));

        return reviews.stream()
                .map(review -> {
                    UserReviewResponse response = toResponse(review, currentUserId,
                            photosByReview.getOrDefault(review.getId(), List.of()),
                            commentCounts.getOrDefault(review.getId(), 0),
                            users.get(review.getUserId()),
                            profiles.get(review.getUserId()),
                            review.getPlaceId() == null ? null : places.get(review.getPlaceId()),
                            review.getActivityBookingId() == null ? null : bookings.get(review.getActivityBookingId()),
                            votes.get(review.getId()));
                    response.setHasSaved(savedIds.contains(review.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    /** One review on its own (detail, create, update, vote): single-row lookups are fine here. */
    private UserReviewResponse mapToResponse(UserReview review, UUID currentUserId) {
        User user = userRepository.findById(review.getUserId()).orElse(null);
        UserReviewProfile profile = profileRepository.findByUserId(review.getUserId()).orElse(null);
        Place place = review.getPlaceId() != null
                ? placeRepository.findById(review.getPlaceId()).orElse(null)
                : null;
        ActivityBooking activityBooking = review.getActivityBookingId() != null
                ? activityBookingRepository.findById(review.getActivityBookingId()).orElse(null)
                : null;

        // Get current user's vote status: true = helpful, false = unhelpful, null = no vote
        Boolean hasVotedHelpful = null;
        if (currentUserId != null) {
            ReviewHelpfulVote vote = voteRepository.findByReviewIdAndUserId(review.getId(), currentUserId);
            if (vote != null) {
                hasVotedHelpful = vote.isHelpful();
            }
        }
        UserReviewResponse response = toResponse(review, currentUserId,
                mediaAssetRepository.findByEntity(USER_REVIEW_ENTITY_TYPE, review.getId()),
                contentCommentRepository.countActiveByTarget(ModeratedContentType.REVIEW, review.getId()),
                user, profile, place, activityBooking, hasVotedHelpful);
        response.setHasSaved(currentUserId != null && !savedPostMapper
                .findSavedIds(currentUserId, SavedPostType.REVIEW, List.of(review.getId())).isEmpty());
        return response;
    }

    private UserReviewResponse toResponse(UserReview review, UUID currentUserId,
                                          List<MediaAsset> photoAssets, int commentCount,
                                          User user, UserReviewProfile profile, Place place,
                                          ActivityBooking activityBooking, Boolean hasVotedHelpful) {
        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(
                photoAssets, user != null ? Map.of(user.getId(), user) : Map.of(), Map.of());

        return UserReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .placeId(review.getPlaceId())
                .activityBookingId(review.getActivityBookingId())
                .tripId(review.getTripId())
                .placeName(place != null ? place.getTitle() : null)
                .placeAddress(place != null ? place.getAddress() : null)
                .placeThumbnail(place != null ? place.getThumbnail() : null)
                .placeReviewCount(place != null ? place.getReviewCount() : null)
                .placeReviewRating(place != null ? place.getReviewRating() : null)
                .placeAdjustedRating(place != null ? place.getAdjustedRating() : null)
                .placeCategory(place != null ? place.getCategory() : null)
                .placeGroup(place != null ? place.getPlaceGroup() : null)
                .placeLatitude(place != null ? place.getLatitude() : null)
                .placeLongitude(place != null ? place.getLongitude() : null)
                .placePhone(place != null ? place.getPhone() : null)
                .placeWebsite(place != null ? place.getWebsite() : null)
                .placePriceRange(place != null ? place.getPriceRange() : null)
                .placeVisitDurationMinutes(place != null ? place.getVisitDurationMinutes() : null)
                .activityBookingTitle(activityBooking != null ? activityBooking.getTitle() : null)
                .activityBookingThumbnail(activityBooking != null ? activityBooking.getThumbnail() : null)
                .activityBookingRating(activityBooking != null ? activityBooking.getRating() : null)
                .activityBookingReviewCount(activityBooking != null ? activityBooking.getReviewCount() : null)
                .activityBookingPriceAmount(activityBooking != null ? activityBooking.getPriceAmount() : null)
                .activityBookingPriceCurrency(activityBooking != null ? activityBooking.getPriceCurrency() : null)
                .userName(user != null ? user.getFullName() : "Unknown")
                .userAvatar(user != null ? user.getAvatarUrl() : null)
                .userTier(profile != null ? profile.getTier() : UserTier.NEWCOMER)
                .overallRating(review.getOverallRating())
                .foodRating(review.getFoodRating())
                .priceRating(review.getPriceRating())
                .ambianceRating(review.getAmbianceRating())
                .serviceRating(review.getServiceRating())
                .locationRating(review.getLocationRating())
                .cleanlinessRating(review.getCleanlinessRating())
                .facilitiesRating(review.getFacilitiesRating())
                .text(review.getText())
                .photos(photoResponses.isEmpty() ? null : MediaAssetResponseMapper.toUrls(photoResponses))
                .photosV2(photoResponses)
                .weight(review.getWeight())
                .helpfulVotes(review.getHelpfulVotes())
                .unhelpfulVotes(review.getUnhelpfulVotes() != null ? review.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(hasVotedHelpful)
                .commentCount(commentCount)
                .isOwnReview(currentUserId != null && review.getUserId().equals(currentUserId))
                .verifiedStay(review.getHotelBookingId() != null || review.getActivityOrderId() != null)
                .hotelBookingId(review.getHotelBookingId())
                .activityOrderId(review.getActivityOrderId())
                .partnerResponse(toPartnerResponse(review))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private static ReviewPartnerResponse toPartnerResponse(UserReview review) {
        if (review.getPartnerResponseText() == null || review.getPartnerResponseText().isBlank()) {
            return null;
        }
        return ReviewPartnerResponse.builder()
                .text(review.getPartnerResponseText())
                .responderName(review.getPartnerResponderName())
                .respondedAt(review.getPartnerRespondedAt())
                .build();
    }

    private void validateReviewTarget(UUID placeId, UUID activityBookingId) {
        boolean hasPlace = placeId != null;
        boolean hasActivityBooking = activityBookingId != null;
        if (hasPlace == hasActivityBooking) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "Exactly one review target is required: placeId or activityBookingId"
            );
        }
    }

    private BigDecimal averageRating(List<UserReview> reviews, Function<UserReview, Integer> ratingGetter) {
        List<Integer> ratings = reviews.stream()
                .map(ratingGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (ratings.isEmpty()) {
            return null;
        }
        double average = ratings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    private String getTierDisplay(UserTier tier) {
        return switch (tier) {
            case NEWCOMER -> "Newcomer ðŸŒ±";
            case TRAVELER -> "Traveler âœˆï¸";
            case EXPLORER -> "Explorer ðŸ§­";
            case EXPERT -> "Local Expert ðŸŒŸ";
        };
    }

    private String getTierDescription(UserTier tier) {
        return switch (tier) {
            case NEWCOMER -> "0-2 reviews";
            case TRAVELER -> "3-9 reviews";
            case EXPLORER -> "10-29 reviews";
            case EXPERT -> "30+ reviews";
        };
    }
}
