package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;
import com.ds.goroute.dto.response.CheckinLinkedReviewResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.CheckinLikeCount;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.event.CheckinCreatedEvent;
import com.ds.goroute.event.CheckinRemovedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCheckinServiceImpl implements UserCheckinService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String PRO_TIER = "PRO";

    /** Place categories where scoring food, price and service actually means something. */
    private static final Set<String> ASPECT_RATING_GROUPS = Set.of("RESTAURANT", "CAFE", "FOOD", "BAR", "HOTEL");

    private final UserCheckinRepository checkinRepository;
    private final UserReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final AiTripRepository subscriptionRepository;
    private final ReviewScoringService scoringService;
    private final ContentModerationService contentModerationService;
    private final BusinessConfigService config;
    private final LocationKeyFactory locationKeyFactory;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public CheckinContextResponse context(UUID userId, UUID placeId, String locationKey) {
        Optional<Place> place = placeId == null ? Optional.empty() : placeRepository.findById(placeId);
        Optional<UserReview> existing = placeId == null
                ? Optional.empty()
                : reviewRepository.findByUserAndPlace(userId, placeId);

        String cluster = locationKey != null ? locationKey
                : place.map(found -> locationKeyFactory.forPlace(found.getId())).orElse(null);
        int previousVisits = cluster == null ? 0 : (int) checkinRepository
                .findByLocationKey(cluster, MAX_PAGE_SIZE, 0).stream()
                .filter(checkin -> userId.equals(checkin.getUserId()))
                .count();

        return CheckinContextResponse.builder()
                .checkinEnabled(config.getBoolean(BusinessConfigKey.CHECKIN_ENABLED))
                .placeInCatalogue(place.isPresent())
                .existingReviewId(existing.map(UserReview::getId).orElse(null))
                .existingReviewText(existing.map(UserReview::getText).orElse(null))
                .existingRating(existing.map(UserReview::getOverallRating).orElse(null))
                .existingFoodRating(existing.map(UserReview::getFoodRating).orElse(null))
                .existingPriceRating(existing.map(UserReview::getPriceRating).orElse(null))
                .existingAmbianceRating(existing.map(UserReview::getAmbianceRating).orElse(null))
                .existingServiceRating(existing.map(UserReview::getServiceRating).orElse(null))
                .existingRatingAt(existing.map(UserReview::getUpdatedAt).orElse(null))
                .showAspectRatings(place.map(this::supportsAspectRatings).orElse(false))
                .previousCheckinCount(previousVisits)
                .galleryAllowed(isGalleryAllowed(userId))
                .maxPhotos(config.getInt(BusinessConfigKey.CHECKIN_MAX_PHOTOS))
                .maxCaptionLength(config.getInt(BusinessConfigKey.CHECKIN_MAX_CAPTION_LENGTH))
                .verifyRadiusMeters(config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS))
                .maxAccuracyMeters(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS))
                .guideScreenEnabled(config.getBoolean(BusinessConfigKey.CHECKIN_GUIDE_SCREEN_ENABLED))
                .guideScreenMaxViews(config.getInt(BusinessConfigKey.CHECKIN_GUIDE_SCREEN_MAX_VIEWS))
                .cameraRewardMultiplier(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_CAMERA_MULTIPLIER))
                .galleryRewardMultiplier(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_GALLERY_MULTIPLIER))
                .build();
    }

    @Override
    @Transactional
    public UserCheckinResponse create(UUID userId, CreateUserCheckinRequest request) {
        requireFeatureEnabled();

        // A repeated submit of the same composition returns what it already produced.
        // Note what this does not do: it never stops the same person checking in at the
        // same place again later, because that is a different moment with its own key.
        Optional<UserCheckin> replayed = checkinRepository.findByIdempotencyKey(userId, request.getIdempotencyKey());
        if (replayed.isPresent()) {
            return toResponse(replayed.get(), true, userId);
        }

        List<CreateUserCheckinRequest.CheckinPhotoInput> photos = request.getPhotos();
        int maxPhotos = config.getInt(BusinessConfigKey.CHECKIN_MAX_PHOTOS);
        if (photos.size() > maxPhotos) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "At most " + maxPhotos + " photos are allowed");
        }

        CheckinPhotoSource photoSource = resolvePhotoSource(photos);
        // The flag only hides the button in the app; refusing the upload here is what
        // actually turns the feature off.
        if (photoSource != CheckinPhotoSource.CAMERA && !isGalleryAllowed(userId)) {
            throw new BusinessException(ErrorConstant.CHECKIN_GALLERY_NOT_ALLOWED,
                    "Choosing photos from your library is not available on your plan right now.");
        }

        Place place = request.getPlaceId() == null ? null
                : placeRepository.findById(request.getPlaceId())
                        .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));

        LocalDateTime now = LocalDateTime.now();
        UserCheckin checkin = UserCheckin.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tripId(request.getTripId())
                .activityId(request.getActivityId())
                .placeId(place == null ? null : place.getId())
                .locationName(request.getLocationName().trim())
                .customName(blankToNull(request.getCustomName()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .ward(blankToNull(request.getWard()))
                .district(blankToNull(request.getDistrict()))
                .province(blankToNull(request.getProvince()))
                .provinceCode(blankToNull(request.getProvinceCode()))
                .locationSource(request.getLocationSource())
                // A catalogued place keys on the place, so everyone who checks in there
                // lands in one cluster whichever door they were standing at.
                .locationKey(place != null
                        ? locationKeyFactory.forPlace(place.getId())
                        : locationKeyFactory.forCoordinates(request.getLatitude(), request.getLongitude()))
                .caption(blankToNull(request.getCaption()))
                .overallRating(request.getOverallRating())
                .foodRating(request.getFoodRating())
                .priceRating(request.getPriceRating())
                .ambianceRating(request.getAmbianceRating())
                .serviceRating(request.getServiceRating())
                .photoSource(photoSource)
                // Current product rule: a check-in is a public community contribution.
                // PRIVATE stays in the enum for a future "only me" mode, not as a hidden
                // client-controlled branch today.
                .visibility(ContentVisibility.PUBLIC)
                .idempotencyKey(request.getIdempotencyKey())
                .isRemoved(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        applyVerification(checkin, place);
        checkinRepository.insert(checkin);
        storePhotos(checkin.getId(), photos, now);

        // The rating half of the rule. A catalogued place gets a real review now; an
        // uncatalogued one keeps the score on the check-in until the cluster is promoted,
        // where it becomes a review without the author having to do anything again.
        if (checkin.hasRating() && checkin.hasCataloguePlace()) {
            UUID reviewId = upsertReview(userId, checkin, photos.stream()
                    .map(CreateUserCheckinRequest.CheckinPhotoInput::getUrl)
                    .toList());
            checkin.setReviewId(reviewId);
            checkinRepository.attachReview(checkin.getId(), reviewId);
            scoringService.recalculatePlaceScores(checkin.getPlaceId());
        }

        events.publishEvent(new CheckinCreatedEvent(checkin.getId(), userId));
        return toResponse(checkin, true, userId);
    }

    @Override
    @Transactional
    public UserCheckinResponse update(UUID userId, UUID checkinId, UpdateUserCheckinRequest request) {
        UserCheckin checkin = requireOwned(userId, checkinId);
        List<CreateUserCheckinRequest.CheckinPhotoInput> photos = request.getPhotos();
        int maxPhotos = config.getInt(BusinessConfigKey.CHECKIN_MAX_PHOTOS);
        if (photos.size() > maxPhotos) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "At most " + maxPhotos + " photos are allowed");
        }

        CheckinPhotoSource photoSource = resolvePhotoSource(photos);
        if (photoSource != CheckinPhotoSource.CAMERA && !isGalleryAllowed(userId)) {
            throw new BusinessException(ErrorConstant.CHECKIN_GALLERY_NOT_ALLOWED,
                    "Choosing photos from your library is not available on your plan right now.");
        }

        checkin.setCaption(blankToNull(request.getCaption()));
        checkin.setCustomName(blankToNull(request.getCustomName()));
        checkin.setOverallRating(request.getOverallRating());
        checkin.setFoodRating(request.getFoodRating());
        checkin.setPriceRating(request.getPriceRating());
        checkin.setAmbianceRating(request.getAmbianceRating());
        checkin.setServiceRating(request.getServiceRating());
        checkin.setPhotoSource(photoSource);
        checkin.setVisibility(ContentVisibility.PUBLIC);
        checkin.setEditedAt(LocalDateTime.now());
        checkin.setUpdatedAt(LocalDateTime.now());

        checkinRepository.deletePhotos(checkinId);
        storePhotos(checkinId, photos, checkin.getUpdatedAt());

        if (checkin.hasRating() && checkin.hasCataloguePlace()) {
            UUID reviewId = upsertReview(userId, checkin, photos.stream()
                    .map(CreateUserCheckinRequest.CheckinPhotoInput::getUrl)
                    .toList());
            checkin.setReviewId(reviewId);
        }

        if (checkinRepository.update(checkin) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        if (checkin.hasCataloguePlace()) {
            scoringService.recalculatePlaceScores(checkin.getPlaceId());
        }
        return toResponse(checkin, true, userId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID checkinId) {
        UserCheckin checkin = requireOwned(userId, checkinId);
        if (checkinRepository.markRemoved(checkinId, userId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        // The review deliberately survives, and the place average does not move. Removing
        // a memory is not the same as retracting an opinion, and somebody clearing out an
        // old photo would never expect it to be.
        events.publishEvent(new CheckinRemovedEvent(checkin.getId(), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserCheckinResponse get(UUID viewerId, UUID checkinId) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));

        boolean isAuthor = checkin.getUserId().equals(viewerId);
        if (!isAuthor && checkin.getVisibility() != ContentVisibility.PUBLIC) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        if (!isAuthor && contentModerationService.isTakenDown(ModeratedContentType.CHECKIN, checkinId)) {
            throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
        }
        return toResponse(checkin, true, viewerId);
    }

    @Override
    @Transactional
    public CheckinLikeResponse toggleLike(UUID userId, UUID checkinId) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> found.getVisibility() == ContentVisibility.PUBLIC
                        && !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        if (contentModerationService.isTakenDown(ModeratedContentType.CHECKIN, checkinId)) {
            throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
        }
        boolean hasLiked = checkinRepository.hasLike(checkinId, userId);
        if (hasLiked) {
            checkinRepository.deleteLike(checkinId, userId);
        } else {
            checkinRepository.insertLike(checkinId, userId);
        }
        return CheckinLikeResponse.builder()
                .checkinId(checkinId)
                .likeCount(checkinRepository.countLikes(checkinId))
                .hasLiked(!hasLiked)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> feed(UUID viewerId, LocalDateTime before, int limit) {
        return toResponses(checkinRepository.findFeed(before, null, boundedSize(limit)), Set.of(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byUser(UUID viewerId, UUID userId, int page, int size) {
        boolean includePrivate = userId.equals(viewerId);
        return toResponses(checkinRepository.findByUser(
                userId, includePrivate, boundedSize(size), boundedPage(page) * boundedSize(size)), Set.of(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byPlace(UUID placeId, int page, int size) {
        List<UserCheckin> checkins = checkinRepository.findByPlace(
                placeId, boundedSize(size), boundedPage(page) * boundedSize(size));
        Set<UUID> latestReviewCheckinIds = checkinRepository.findLatestRatedPerUserForPlace(placeId).stream()
                .map(UserCheckin::getId)
                .collect(Collectors.toSet());
        return toResponses(checkins, latestReviewCheckinIds, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byLocationKey(String locationKey, int page, int size) {
        return toResponses(checkinRepository.findByLocationKey(
                locationKey, boundedSize(size), boundedPage(page) * boundedSize(size)), Set.of(), null);
    }

    // --- rules -----------------------------------------------------------------------

    /**
     * Verification is computed here and nowhere else, from the three things the server can
     * actually check: whether the photo was taken through the in-app camera, how good the
     * fix was, and how far the author was from the place.
     *
     * <p>A gallery photo is never automatically verified. There is no way to know where or
     * when it was taken, and saying otherwise would make the badge meaningless everywhere.
     */
    private void applyVerification(UserCheckin checkin, Place place) {
        Double distance = place == null ? null : GeoDistance.betweenOrNull(
                checkin.getLatitude(), checkin.getLongitude(),
                place.getLatitude(), place.getLongitude());
        if (distance != null) {
            checkin.setDistanceMeters(BigDecimal.valueOf(distance));
        }

        if (!checkin.getPhotoSource().isLiveCapture()) {
            checkin.setVerificationStatus(CheckinVerificationStatus.UNVERIFIED);
            return;
        }
        int maxAccuracy = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        if (checkin.getAccuracyMeters() == null || checkin.getAccuracyMeters().doubleValue() > maxAccuracy) {
            checkin.setVerificationStatus(CheckinVerificationStatus.UNVERIFIED);
            return;
        }
        int radius = config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS);
        boolean withinRadius = distance == null || distance <= radius;
        checkin.setVerificationStatus(withinRadius
                ? CheckinVerificationStatus.VERIFIED
                : CheckinVerificationStatus.UNVERIFIED);
    }

    /**
     * Creates the author's review of the place, or updates the one they already had.
     * Never a second review: the table holds one per person per place, and revisiting
     * means changing your mind rather than holding two opinions.
     */
    private UUID upsertReview(UUID userId, UserCheckin checkin, List<String> photoUrls) {
        Optional<UserReview> existing = reviewRepository.findByUserAndPlace(userId, checkin.getPlaceId());
        LocalDateTime now = LocalDateTime.now();
        String reviewPhotos = photoUrls.isEmpty() ? null : JsonUtils.toJson(photoUrls);

        if (existing.isPresent()) {
            UserReview review = existing.get();
            review.setOverallRating(checkin.getOverallRating());
            review.setFoodRating(checkin.getFoodRating());
            review.setPriceRating(checkin.getPriceRating());
            review.setAmbianceRating(checkin.getAmbianceRating());
            review.setServiceRating(checkin.getServiceRating());
            // The newest rated check-in is the current review. Keep the two surfaces
            // identical instead of leaving old review prose beside new check-in media.
            review.setText(checkin.getCaption());
            review.setPhotos(reviewPhotos);
            review.setCheckinLat(checkin.getLatitude());
            review.setCheckinLng(checkin.getLongitude());
            review.setCheckinAccuracy(checkin.getAccuracyMeters());
            review.setLocationVerified(checkin.getVerificationStatus() == CheckinVerificationStatus.VERIFIED);
            review.setUpdatedAt(now);
            reviewRepository.update(review);
            return review.getId();
        }

        UserReview review = UserReview.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(checkin.getPlaceId())
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
                .locationVerified(checkin.getVerificationStatus() == CheckinVerificationStatus.VERIFIED)
                .weight(BigDecimal.ONE)
                .helpfulVotes(0)
                .unhelpfulVotes(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reviewRepository.save(review);
        return review.getId();
    }

    private CheckinPhotoSource resolvePhotoSource(List<CreateUserCheckinRequest.CheckinPhotoInput> photos) {
        return photos.stream()
                .map(CreateUserCheckinRequest.CheckinPhotoInput::getSource)
                .reduce(CheckinPhotoSource::combine)
                .orElse(CheckinPhotoSource.GALLERY);
    }

    private void storePhotos(UUID checkinId, List<CreateUserCheckinRequest.CheckinPhotoInput> photos,
                             LocalDateTime now) {
        int position = 0;
        for (CreateUserCheckinRequest.CheckinPhotoInput photo : photos) {
            checkinRepository.insertPhoto(UserCheckinPhoto.builder()
                    .id(UUID.randomUUID())
                    .checkinId(checkinId)
                    .url(photo.getUrl())
                    .source(photo.getSource())
                    .position(position++)
                    .capturedAt(photo.getCapturedAt())
                    .latitude(photo.getLatitude())
                    .longitude(photo.getLongitude())
                    .accuracyMeters(photo.getAccuracyMeters())
                    .createdAt(now)
                    .build());
        }
    }

    private boolean isGalleryAllowed(UUID userId) {
        if (PRO_TIER.equalsIgnoreCase(subscriptionRepository.getSubscriptionTier(userId))) {
            return true;
        }
        return config.getBoolean(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE);
    }

    private boolean supportsAspectRatings(Place place) {
        String group = place.getPlaceGroup() == null ? null : place.getPlaceGroup().name();
        return group != null && ASPECT_RATING_GROUPS.contains(group);
    }

    private void requireFeatureEnabled() {
        if (!config.getBoolean(BusinessConfigKey.CHECKIN_ENABLED)) {
            throw new BusinessException(ErrorConstant.CHECKIN_FEATURE_DISABLED,
                    "Check-in is currently unavailable.");
        }
    }

    private UserCheckin requireOwned(UUID userId, UUID checkinId) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        if (!checkin.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only change your own check-ins");
        }
        return checkin;
    }

    // --- mapping ---------------------------------------------------------------------

    private List<UserCheckinResponse> toResponses(List<UserCheckin> checkins) {
        return toResponses(checkins, Set.of(), null);
    }

    private List<UserCheckinResponse> toResponses(List<UserCheckin> checkins,
                                                   Set<UUID> latestReviewCheckinIds,
                                                   UUID viewerId) {
        if (checkins.isEmpty()) {
            return List.of();
        }
        // One query for the photos of the whole page and one for the authors, rather than
        // two per row.
        Map<UUID, List<UserCheckinPhoto>> photos = checkinRepository
                .findPhotosForCheckins(checkins.stream().map(UserCheckin::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(UserCheckinPhoto::getCheckinId));
        Map<UUID, User> authors = loadAuthors(checkins);
        List<UUID> checkinIds = checkins.stream().map(UserCheckin::getId).toList();
        Map<UUID, Integer> likeCounts = checkinRepository.findLikeCounts(checkinIds).stream()
                .collect(Collectors.toMap(CheckinLikeCount::getCheckinId,
                        count -> count.getLikeCount() == null ? 0 : count.getLikeCount()));
        Set<UUID> likedIds = viewerId == null ? Set.of()
                : new java.util.HashSet<>(checkinRepository.findLikedCheckinIds(viewerId, checkinIds));
        Map<UUID, UserReview> linkedReviews = reviewRepository.findByIds(checkins.stream()
                        .map(UserCheckin::getReviewId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(UserReview::getId, review -> review));

        return checkins.stream()
                .map(checkin -> toResponse(checkin, photos.getOrDefault(checkin.getId(), List.of()),
                        authors.get(checkin.getUserId()), latestReviewCheckinIds.contains(checkin.getId()),
                        linkedReviews.get(checkin.getReviewId()), likeCounts.getOrDefault(checkin.getId(), 0),
                        likedIds.contains(checkin.getId())))
                .toList();
    }

    private Map<UUID, User> loadAuthors(List<UserCheckin> checkins) {
        Set<UUID> authorIds = checkins.stream()
                .map(UserCheckin::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return authorIds.stream()
                .map(userRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));
    }

    private UserCheckinResponse toResponse(UserCheckin checkin, boolean loadPhotos, UUID viewerId) {
        List<UserCheckinPhoto> photos = loadPhotos ? checkinRepository.findPhotos(checkin.getId()) : List.of();
        UserReview linkedReview = checkin.getReviewId() == null ? null
                : reviewRepository.findById(checkin.getReviewId()).orElse(null);
        return toResponse(checkin, photos, userRepository.findById(checkin.getUserId()).orElse(null), false,
                linkedReview, checkinRepository.countLikes(checkin.getId()),
                viewerId != null && checkinRepository.hasLike(checkin.getId(), viewerId));
    }

    private UserCheckinResponse toResponse(UserCheckin checkin, List<UserCheckinPhoto> photos, User author,
                                           boolean latestReview, UserReview linkedReview, int likeCount,
                                           boolean hasLiked) {
        return UserCheckinResponse.builder()
                .id(checkin.getId())
                .userId(checkin.getUserId())
                .userDisplayName(author == null ? null : author.getFullName())
                .userAvatarUrl(author == null ? null : author.getAvatarUrl())
                .tripId(checkin.getTripId())
                .activityId(checkin.getActivityId())
                .placeId(checkin.getPlaceId())
                .reviewId(checkin.getReviewId())
                .locationName(checkin.getLocationName())
                .customName(checkin.getCustomName())
                .latitude(checkin.getLatitude())
                .longitude(checkin.getLongitude())
                .ward(checkin.getWard())
                .district(checkin.getDistrict())
                .province(checkin.getProvince())
                .provinceCode(checkin.getProvinceCode())
                .locationSource(checkin.getLocationSource())
                .locationKey(checkin.getLocationKey())
                .caption(checkin.getCaption())
                .overallRating(checkin.getOverallRating())
                .foodRating(checkin.getFoodRating())
                .priceRating(checkin.getPriceRating())
                .ambianceRating(checkin.getAmbianceRating())
                .serviceRating(checkin.getServiceRating())
                .photoSource(checkin.getPhotoSource())
                .visibility(checkin.getVisibility())
                .verificationStatus(checkin.getVerificationStatus())
                .distanceMeters(checkin.getDistanceMeters())
                .rewardPoints(checkin.getRewardPoints())
                .rewardReason(checkin.getRewardReason())
                .edited(checkin.getEditedAt() != null)
                .createdAt(checkin.getCreatedAt())
                .linkedToReview(checkin.getReviewId() != null)
                .latestReview(latestReview)
                .linkedReview(toLinkedReview(linkedReview))
                .likeCount(likeCount)
                .hasLiked(hasLiked)
                .photos(photos.stream()
                        .sorted(Comparator.comparing(UserCheckinPhoto::getPosition,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(photo -> CheckinPhotoResponse.builder()
                                .id(photo.getId())
                                .url(photo.getUrl())
                                .source(photo.getSource())
                                .position(photo.getPosition())
                                .capturedAt(photo.getCapturedAt())
                                .build())
                        .toList())
                .build();
    }

    private CheckinLinkedReviewResponse toLinkedReview(UserReview review) {
        if (review == null) {
            return null;
        }
        List<String> photos = review.getPhotos() == null ? List.of()
                : JsonUtils.fromJson(review.getPhotos(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        if (photos == null) {
            photos = List.of();
        }
        return CheckinLinkedReviewResponse.builder()
                .id(review.getId())
                .overallRating(review.getOverallRating())
                .foodRating(review.getFoodRating())
                .priceRating(review.getPriceRating())
                .ambianceRating(review.getAmbianceRating())
                .serviceRating(review.getServiceRating())
                .text(review.getText())
                .photos(photos)
                .helpfulVotes(review.getHelpfulVotes())
                .unhelpfulVotes(review.getUnhelpfulVotes())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private int boundedSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private int boundedPage(int page) {
        return Math.max(0, page);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
