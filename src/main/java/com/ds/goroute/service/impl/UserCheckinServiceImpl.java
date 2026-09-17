package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;
import com.ds.goroute.dto.response.CheckinLinkedReviewResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Checkin;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.CheckinLikeCount;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.event.CheckinCreatedEvent;
import com.ds.goroute.event.CheckinRemovedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.checkin.CheckinRewardCalculator;
import com.ds.goroute.service.checkin.CheckinRewardService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.MediaAssetResponseMapper;
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

    /** How close to a stop of the itinerary counts as having been there. */
    private static final double ACTIVITY_VISIT_RADIUS_METERS = 200;

    /** Place categories where scoring food, price and service actually means something. */
    private static final Set<String> ASPECT_RATING_GROUPS = Set.of("RESTAURANT", "CAFE", "FOOD", "BAR", "HOTEL");

    private final UserCheckinRepository checkinRepository;
    private final UserReviewRepository reviewRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final AiTripRepository subscriptionRepository;
    private final ReviewScoringService scoringService;
    private final ReviewService reviewService;
    private final ContentModerationService contentModerationService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final BusinessConfigService config;
    private final LocationKeyFactory locationKeyFactory;
    private final ApplicationEventPublisher events;
    private final SocialNotificationService socialNotificationService;
    private final TripAccessGuard tripAccessGuard;
    private final CheckinRewardService rewardService;
    private final ActivityRepository activityRepository;
    private final CheckinRepository activityVisitRepository;
    private final NotificationHelper notificationHelper;

    @Override
    @Transactional(readOnly = true)
    public CheckinContextResponse context(UUID userId, UUID placeId, String locationKey,
                                          BigDecimal latitude, BigDecimal longitude,
                                          BigDecimal accuracyMeters) {
        Optional<Place> place = placeId == null ? Optional.empty() : placeRepository.findById(placeId);
        Optional<UserReview> existing = placeId == null
                ? Optional.empty()
                : reviewRepository.findByUserAndPlace(userId, placeId);

        String cluster = locationKey != null ? locationKey
                : place.map(found -> locationKeyFactory.forPlace(found.getId())).orElse(null);
        // Do not derive a user's progress from the public cluster page. That query is
        // capped at 50 rows and can contain only other users' recent visits, making a
        // returning author look like a first-time visitor. Count the author's rows
        // directly so the Place Detail state is deterministic.
        long previousVisitCount = placeId != null
                ? checkinRepository.countByUserAndPlace(userId, placeId)
                : cluster == null
                ? 0L
                : checkinRepository.countByUserAndLocationKey(userId, cluster);
        int previousVisits = (int) Math.min(Integer.MAX_VALUE, previousVisitCount);

        int globalRadius = config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS);
        int effectiveRadius = effectiveVerificationRadius(place.orElse(null), globalRadius);
        Double distanceMeters = place.map(found -> GeoDistance.betweenOrNull(
                latitude, longitude, found.getLatitude(), found.getLongitude())).orElse(null);
        Boolean withinVerificationRadius = distanceMeters == null
                ? null
                : distanceMeters <= effectiveRadius;
        int maxAccuracyMeters = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        Boolean gpsAccuracyAcceptable = accuracyMeters == null
                ? null
                : accuracyMeters.doubleValue() <= maxAccuracyMeters;

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
                .verifyRadiusMeters(effectiveRadius)
                .placeSpecificRadius(place.map(Place::getVerificationRadiusMeters)
                        .map(value -> value >= 20)
                        .orElse(false))
                .distanceMeters(distanceMeters)
                .withinVerificationRadius(withinVerificationRadius)
                .gpsAccuracyAcceptable(gpsAccuracyAcceptable)
                .maxAccuracyMeters(maxAccuracyMeters)
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

        // Checked before anything is written: the trip id decides who gets notified, so an
        // unverified one would let anybody post into a stranger's trip.
        Activity activity = requireTripContext(request.getTripId(), request.getActivityId(), userId);

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

        // Worked out here rather than only in the listener, so the response the author is looking
        // at carries the real number. The listener used to be the only place this happened, which
        // is why the app could never say "+5" at the moment it was earned -- the field was still
        // null when the response left. The wallet entry stays asynchronous; it is the ledger write
        // that must not be able to take a check-in down with it, not the arithmetic.
        rewardService.record(checkin);

        events.publishEvent(new CheckinCreatedEvent(checkin.getId(), userId));
        // The two halves of the retired activity check-in, kept. The itinerary still gets
        // its "visited" mark, and the people travelling together are still told -- whether
        // the visit landed on a scheduled activity or on somewhere the itinerary never
        // mentioned.
        markActivityVisited(activity, checkin, userId);
        if (checkin.getTripId() != null) {
            notificationHelper.emitCheckin(checkin.getTripId(), checkin.getActivityId(), userId,
                    activity != null ? activity.getName() : displayName(checkin));
        }
        return toResponse(checkin, true, userId);
    }

    /**
     * Records that this traveller has now been to this stop of the itinerary.
     *
     * <p>The trip's own record of a visit, which the activity card counts, kept alive now
     * that the endpoint that used to write it is gone. Its rule is unchanged: near the
     * activity, in person. Being too far away is not an error -- the check-in is still a
     * real post about somewhere -- so it only means the itinerary is not marked.
     */
    private void markActivityVisited(Activity activity, UserCheckin checkin, UUID userId) {
        if (activity == null || activity.getLat() == null || activity.getLng() == null) {
            return;
        }
        Double distance = GeoDistance.betweenOrNull(checkin.getLatitude(), checkin.getLongitude(),
                activity.getLat(), activity.getLng());
        if (distance == null || distance > ACTIVITY_VISIT_RADIUS_METERS) {
            return;
        }
        if (activityVisitRepository.findByActivityIdAndUserId(activity.getId(), userId).isPresent()) {
            return;
        }
        activityVisitRepository.insert(Checkin.builder()
                .id(UUID.randomUUID())
                .activityId(activity.getId())
                .userId(userId)
                .lat(checkin.getLatitude())
                .lng(checkin.getLongitude())
                .autoCheckin(false)
                .build());
    }

    /**
     * Verifies a check-in that says it belongs to a trip actually may.
     *
     * @return the activity when the check-in is filed under one, null when it is attached to
     *         the trip alone or to no trip at all
     */
    private Activity requireTripContext(UUID tripId, UUID activityId, UUID userId) {
        if (tripId == null) {
            if (activityId != null) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "An activity check-in needs its trip");
            }
            return null;
        }
        tripAccessGuard.requireAccess(tripId, userId);
        if (activityId == null) {
            return null;
        }
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }
        return activity;
    }

    /** What the trip should call this place: the author's own name for it wins. */
    private String displayName(UserCheckin checkin) {
        return checkin.getCustomName() != null ? checkin.getCustomName() : checkin.getLocationName();
    }

    @Override
    @Transactional
    public UserCheckinResponse update(UUID userId, UUID checkinId, UpdateUserCheckinRequest request) {
        UserCheckin checkin = requireOwned(userId, checkinId);
        List<CreateUserCheckinRequest.CheckinPhotoInput> photos = request.getPhotos();
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

        List<String> keptUrls = photos.stream()
                .map(CreateUserCheckinRequest.CheckinPhotoInput::getUrl)
                .toList();

        // The review goes first so the cleanup below asks it what it shows *after* this
        // edit. Asked before, it would still be listing the photo the author just removed
        // and would keep the file alive for a post that no longer has it.
        if (checkin.hasRating() && checkin.hasCataloguePlace()) {
            checkin.setReviewId(upsertReview(userId, checkin, keptUrls));
        }

        // A photo taken off the visit is taken off it now. The orphan sweep is run by hand
        // and only after a grace period, so leaving these to it means paying to store
        // photos nobody can see until somebody remembers to look. Runs while the old rows
        // are still readable -- deletePhotos below is what makes them unreadable -- and
        // spares anything the review it just wrote still displays.
        imageStorageCleanupService.deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", checkinId, keptUrls);

        checkinRepository.deletePhotos(checkinId);
        storePhotos(checkinId, photos, checkin.getUpdatedAt());

        if (checkinRepository.update(checkin) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        if (checkin.hasCataloguePlace()) {
            scoringService.recalculatePlaceScores(checkin.getPlaceId());
        }
        notifyTripCheckinUpdated(checkin, userId);
        return toResponse(checkin, true, userId);
    }

    /**
     * Editing a visit made during a trip is the trip's business too -- it is the same
     * moment being corrected, which is what the old activity check-in said when somebody
     * checked in at the same activity twice.
     */
    private void notifyTripCheckinUpdated(UserCheckin checkin, UUID userId) {
        UUID tripId = checkin.getTripId();
        if (tripId == null) {
            return;
        }
        UUID activityId = checkin.getActivityId();
        Activity activity = activityId == null ? null : activityRepository.findById(activityId).orElse(null);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("actorName", notificationHelper.actorName(userId));
        data.put("activityName", activity != null ? activity.getName() : displayName(checkin));
        data.put("tripName", notificationHelper.tripName(tripId));
        data.put("deepLink", activityId == null
                ? "/trip/" + tripId
                : "/trip/" + tripId + "/activities/" + activityId);
        if (activityId != null) {
            data.put("activityId", activityId);
        }
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.CHECKIN_UPDATED, data, null);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID checkinId, boolean deleteReview) {
        UserCheckin checkin = requireOwned(userId, checkinId);
        // Collected before the row is marked removed, because the photo rows are read
        // through the check-in and the retain query only spares files the surviving
        // review still shows. The delete itself runs after this transaction commits.
        imageStorageCleanupService.deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", checkinId);
        if (checkinRepository.markRemoved(checkinId, userId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        // The check-in itself remains as a soft-deleted audit record, but its photo rows
        // are no longer needed. The storage cleanup above has already protected any URL
        // that the surviving linked review still displays.
        checkinRepository.deletePhotos(checkinId);
        // Removing a memory is not the same as retracting an opinion, so by default the
        // review survives and the place average does not move. The author can ask for both
        // to go, because a check-in is now the only way they can write that review at all
        // and refusing would leave it with no owner.
        if (deleteReview && checkin.getReviewId() != null) {
            reviewService.deleteReview(userId, checkin.getReviewId());
        }
        events.publishEvent(new CheckinRemovedEvent(checkin.getId(), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserCheckinResponse findMine(UUID userId, UUID activityId, UUID placeId, UUID tripId) {
        if (activityId == null && placeId == null && tripId == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "An activity, place or trip is required");
        }
        return checkinRepository.findMine(userId, activityId, placeId, tripId)
                .map(checkin -> toResponse(checkin, true, userId))
                .orElse(null);
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
            socialNotificationService.notifyLike(
                    checkin.getUserId(), userId, ModeratedContentType.CHECKIN.name(), checkinId);
        }
        return CheckinLikeResponse.builder()
                .checkinId(checkinId)
                .likeCount(checkinRepository.countLikes(checkinId))
                .hasLiked(!hasLiked)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> feed(UUID viewerId, int page, int size) {
        return toResponses(checkinRepository.findFeed(
                viewerId, boundedSize(size), boundedPage(page) * boundedSize(size)), Set.of(), viewerId);
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
        // A raw map/reverse-geocoded point has no catalogue boundary to compare against.
        // Camera + GPS accuracy alone proves where the phone was, not that it was at a
        // particular Place. Province-wide Passport tags may still count this event, but
        // the trust badge must remain unverified until the point is linked to a Place.
        if (place == null) {
            checkin.setVerificationStatus(CheckinVerificationStatus.UNVERIFIED);
            return;
        }
        int maxAccuracy = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        if (checkin.getAccuracyMeters() == null || checkin.getAccuracyMeters().doubleValue() > maxAccuracy) {
            checkin.setVerificationStatus(CheckinVerificationStatus.UNVERIFIED);
            return;
        }
        int radius = effectiveVerificationRadius(place,
                config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS));
        boolean withinRadius = distance != null && distance <= radius;
        checkin.setVerificationStatus(withinRadius
                ? CheckinVerificationStatus.VERIFIED
                : CheckinVerificationStatus.UNVERIFIED);
    }

    private int effectiveVerificationRadius(Place place, int globalRadius) {
        Integer configured = place == null ? null : place.getVerificationRadiusMeters();
        return configured != null && configured >= 20 ? configured : globalRadius;
    }

    /**
     * Creates the author's review of the place, or updates the one they already had.
     * Never a second review: the table holds one per person per place, and revisiting
     * means changing your mind rather than holding two opinions.
     */
    private UUID upsertReview(UUID userId, UserCheckin checkin, List<String> photoUrls) {
        Optional<UserReview> existing = reviewRepository.findByUserAndPlace(userId, checkin.getPlaceId());
        LocalDateTime now = LocalDateTime.now();
        String reviewPhotos = JsonUtils.toJson(photoUrls);

        if (existing.isPresent()) {
            UserReview review = existing.get();
            review.setOverallRating(checkin.getOverallRating());
            review.setFoodRating(checkin.getFoodRating());
            review.setPriceRating(checkin.getPriceRating());
            review.setAmbianceRating(checkin.getAmbianceRating());
            review.setServiceRating(checkin.getServiceRating());
            // The newest rated check-in is the current review. Keep the two surfaces
            // identical instead of leaving old review prose beside new check-in media --
            // but a visit the author left wordless is not them retracting what they wrote
            // last time, so an empty caption keeps the existing text rather than erasing it.
            if (checkin.getCaption() != null && !checkin.getCaption().isBlank()) {
                review.setText(checkin.getCaption());
            }
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
                    .title(photo.getTitle())
                    .description(photo.getDescription())
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
        List<UUID> reviewIds = checkins.stream()
                .map(UserCheckin::getReviewId).filter(Objects::nonNull).distinct().toList();
        // A review taken down by moderation is not shown, even inside the check-in that wrote it.
        Set<UUID> takenDownReviewIds = contentModerationService.takenDownIds(ModeratedContentType.REVIEW, reviewIds);
        Map<UUID, UserReview> linkedReviews = reviewRepository.findByIds(reviewIds).stream()
                .filter(review -> !takenDownReviewIds.contains(review.getId()))
                .collect(Collectors.toMap(UserReview::getId, review -> review));
        Map<UUID, Place> places = loadPlaces(checkins);

        return checkins.stream()
                .map(checkin -> toResponse(checkin, photos.getOrDefault(checkin.getId(), List.of()),
                        authors.getOrDefault(checkin.getUserId(), null), latestReviewCheckinIds.contains(checkin.getId()),
                        linkedReviews.get(checkin.getReviewId()), likeCounts.getOrDefault(checkin.getId(), 0),
                        likedIds.contains(checkin.getId()), 
                        checkin.getPlaceId() == null ? null : places.get(checkin.getPlaceId())))
                .toList();
    }

    /**
     * One query for the places on the whole page. The feed post renders a place card, and
     * looking the place up per row is how a twenty-item feed turns into twenty-one queries.
     */
    private Map<UUID, Place> loadPlaces(List<UserCheckin> checkins) {
        List<UUID> placeIds = checkins.stream()
                .map(UserCheckin::getPlaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findByIds(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (first, second) -> first));
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
        UserReview linkedReview = checkin.getReviewId() == null
                || contentModerationService.isTakenDown(ModeratedContentType.REVIEW, checkin.getReviewId())
                ? null
                : reviewRepository.findById(checkin.getReviewId()).orElse(null);
        Place place = checkin.getPlaceId() == null ? null
                : placeRepository.findById(checkin.getPlaceId()).orElse(null);
        return toResponse(checkin, photos, userRepository.findById(checkin.getUserId()).orElse(null), false,
                linkedReview, checkinRepository.countLikes(checkin.getId()),
                viewerId != null && checkinRepository.hasLike(checkin.getId(), viewerId), place);
    }

    private UserCheckinResponse toResponse(UserCheckin checkin, List<UserCheckinPhoto> photos, User author,
                                           boolean latestReview, UserReview linkedReview, int likeCount,
                                           boolean hasLiked, Place place) {
        List<MemoryImageResponse> photoResponses = photos.stream()
                .sorted(Comparator.comparing(UserCheckinPhoto::getPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(photo -> MemoryImageResponse.builder()
                        .id(photo.getId())
                        .url(photo.getUrl())
                        .entityType("USER_CHECKIN")
                        .entityId(checkin.getId())
                        .mediaType("IMAGE")
                        .position(photo.getPosition())
                        .title(photo.getTitle())
                        .description(photo.getDescription())
                        .takenAt(photo.getCapturedAt())
                        .dateSource(photo.getCapturedAt() == null ? "UPLOAD" : "CAPTURE")
                        .captureSource(photo.getSource() == null ? null : photo.getSource().name())
                        .latitude(photo.getLatitude())
                        .longitude(photo.getLongitude())
                        .accuracyMeters(photo.getAccuracyMeters())
                        .placeId(checkin.getPlaceId())
                        .locationName(checkin.getLocationName())
                        .locationSource(checkin.getLocationSource() == null
                                ? null : checkin.getLocationSource().name())
                        .createdAt(photo.getCreatedAt())
                        .uploadedBy(checkin.getUserId())
                        .uploaderName(author == null ? null : author.getFullName())
                        .uploaderAvatarUrl(author == null ? null : author.getAvatarUrl())
                        .activityId(checkin.getActivityId())
                        .build())
                .toList();

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
                .placeName(place == null ? null : place.getTitle())
                .placeAddress(place == null ? null : place.getAddress())
                .placeThumbnail(place == null ? null : place.getThumbnail())
                .placeReviewCount(place == null ? null : place.getReviewCount())
                .placeReviewRating(place == null ? null : place.getReviewRating())
                .placeAdjustedRating(place == null ? null : place.getAdjustedRating())
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
                .rewardReasonCodes(CheckinRewardCalculator.parseReasonCodes(checkin.getRewardReason()))
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
                                .title(photo.getTitle())
                                .description(photo.getDescription())
                                .capturedAt(photo.getCapturedAt())
                                .latitude(photo.getLatitude())
                                .longitude(photo.getLongitude())
                                .accuracyMeters(photo.getAccuracyMeters())
                                .build())
                        .toList())
                .photosV2(photoResponses)
                .build();
    }

    private CheckinLinkedReviewResponse toLinkedReview(UserReview review) {
        if (review == null) {
            return null;
        }
        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(
                mediaAssetRepository.findByEntity("USER_REVIEW", review.getId()));
        List<String> photos = photoResponses.isEmpty()
                ? List.of()
                : MediaAssetResponseMapper.toUrls(photoResponses);
        return CheckinLinkedReviewResponse.builder()
                .id(review.getId())
                .overallRating(review.getOverallRating())
                .foodRating(review.getFoodRating())
                .priceRating(review.getPriceRating())
                .ambianceRating(review.getAmbianceRating())
                .serviceRating(review.getServiceRating())
                .text(review.getText())
                .photos(photos)
                .photosV2(photoResponses)
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
