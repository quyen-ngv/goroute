package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.FeedCursor;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.CheckinVideoUploadResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;
import com.ds.goroute.dto.response.CheckinLinkedReviewResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.MusicTrackResponse;
import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Checkin;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.entity.CheckinLikeCount;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.event.CheckinCreatedEvent;
import com.ds.goroute.event.CheckinRemovedEvent;
import com.ds.goroute.event.PlaceUsageChangedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.mapper.SavedPostMapper;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.FeedImpressionService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.MusicTrackService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserGuideService;
import com.ds.goroute.service.checkin.CheckinRewardCalculator;
import com.ds.goroute.service.checkin.CheckinRewardService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.dto.response.VisitedWardResponse;
import com.ds.goroute.type.CheckinVerificationScope;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.CheckinMediaType;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.FeedItemType;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.SavedPostType;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.Mp4Duration;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import com.ds.goroute.utils.PlaceDisplayRating;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCheckinServiceImpl implements UserCheckinService {

    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Ceiling on the check-ins one shared trip carries. The page is public and renders
     * every one of them at once, so a trip somebody checked into hundreds of times must
     * not decide how much work a stranger's request is.
     */
    private static final int MAX_TRIP_CHECKINS = 200;
    private static final String PRO_TIER = "PRO";

    /** Storage prefix of check-in videos; the user id follows. */
    private static final String VIDEO_PREFIX = "checkin-videos/";

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
    private final CheckinVerifier verifier;
    private final com.ds.goroute.quest.service.QuestPlayService questPlayService;
    private final ContentCommentRepository contentCommentRepository;
    private final SavedPostMapper savedPostMapper;
    private final FeedImpressionService feedImpressionService;
    private final FileUploadService fileUploadService;
    private final BetaAccessService betaAccess;
    private final UserGuideService userGuideService;
    private final MusicTrackService musicTrackService;
    private final com.ds.goroute.localevent.persistence.LocalEventRepository localEventRepository;

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

        // The same assessment the verdict will be reached with, so the hint on screen
        // never disagrees with the badge the check-in gets a moment later.
        CheckinVerifier.Assessment assessment = verifier.assess(place.orElse(null), latitude, longitude, accuracyMeters);
        int maxAccuracyMeters = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        boolean pointSupplied = latitude != null && longitude != null;

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
                .videoAllowed(isVideoAllowed(userId))
                .maxVideoSeconds(config.getInt(BusinessConfigKey.CHECKIN_MAX_VIDEO_SECONDS))
                .musicAllowed(isMusicAllowed(userId))
                .maxCaptionLength(config.getInt(BusinessConfigKey.CHECKIN_MAX_CAPTION_LENGTH))
                .verifyRadiusMeters(assessment.effectiveRadiusMeters())
                .placeSpecificRadius(place.map(Place::getVerificationRadiusMeters)
                        .map(value -> value >= 20)
                        .orElse(false))
                .distanceMeters(assessment.distanceMeters())
                .withinVerificationRadius(assessment.withinPlaceArea())
                .placeHasGeometry(assessment.placeHasGeometry())
                .gpsAccuracyAcceptable(assessment.accuracyAcceptable())
                .maxAccuracyMeters(maxAccuracyMeters)
                .wardCode(assessment.ward().map(Ward::getCode).orElse(null))
                .wardName(assessment.ward().map(Ward::getFullName).orElse(null))
                .provinceCode(assessment.ward().map(Ward::getProvinceCode).orElse(null))
                .provinceName(assessment.ward().map(Ward::getProvinceName).orElse(null))
                .verificationScopePreview(pointSupplied ? assessment.scopeIfLiveCapture() : null)
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
        requireAllowedMedia(userId, photos, Set.of());
        requireAllowedMusic(userId, request.getMusicTrackId(), null);

        // A check-in made at a local event. An event that was unpublished or deleted while the
        // post waited in the offline queue must not fail the post: it is simply not linked.
        com.ds.goroute.localevent.domain.LocalEvent localEvent = request.getLocalEventId() == null ? null
                : localEventRepository.findPublishedById(request.getLocalEventId()).orElse(null);

        // An event held at a catalogue place is a visit to that place too, whether or not the
        // app knew the link when the author picked the event.
        UUID placeId = request.getPlaceId() != null ? request.getPlaceId()
                : localEvent == null ? null : localEvent.getPlaceId();
        Place place = placeId == null ? null
                : request.getPlaceId() != null
                ? placeRepository.findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"))
                : placeRepository.findById(placeId).orElse(null);

        // Checked before anything is written: the trip id decides who gets notified, so an
        // unverified one would let anybody post into a stranger's trip.
        Activity activity = requireTripContext(request.getTripId(), request.getActivityId(), userId);

        // A check-in filed under a trip stop that is an event is a check-in at that event,
        // whether or not the app said so.
        if (localEvent == null && activity != null && activity.getLocalEventId() != null) {
            localEvent = localEventRepository.findPublishedById(activity.getLocalEventId()).orElse(null);
        }

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
                .questRunId(request.getQuestRunId())
                .localEventId(localEvent == null ? null : localEvent.getId())
                // A catalogued place keys on the place, so everyone who checks in there
                // lands in one cluster whichever door they were standing at.
                .locationKey(place != null
                        ? locationKeyFactory.forPlace(place.getId())
                        : locationKeyFactory.forCoordinates(request.getLatitude(), request.getLongitude()))
                .caption(blankToNull(request.getCaption()))
                .frameStyle(request.getFrameStyle())
                // A catalogue place has its own kind, and it is the one that counts.
                .placeKind(place == null ? request.getPlaceKind() : null)
                .musicTrackId(request.getMusicTrackId())
                .muteVideoSound(Boolean.TRUE.equals(request.getMuteVideoSound()))
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
                .visitedAt(resolveVisitedAt(request.getVisitedAt(), now))
                .createdAt(now)
                .updatedAt(now)
                .build();

        VerificationTarget eventVenue = eventVenueTarget(localEvent, place);
        if (eventVenue != null) {
            verifier.applyTo(checkin, eventVenue);
        } else {
            verifier.apply(checkin, place);
        }
        checkinRepository.insert(checkin);
        storePhotos(checkin.getId(), photos, now);

        // A check-in made to satisfy a quest checkpoint is linked back to the run (D13); the
        // checkpoint then counts as passed. Best-effort so a quest hiccup never fails the check-in.
        if (request.getQuestRunId() != null) {
            questPlayService.attachCheckin(request.getQuestRunId(), request.getQuestCheckpointId(),
                    userId, checkin.getId());
        }

        // The rating half of the rule. A catalogued place gets a real review now; an
        // uncatalogued one keeps the score on the check-in until the cluster is promoted,
        // where it becomes a review without the author having to do anything again.
        if (checkin.hasRating() && checkin.hasCataloguePlace()) {
            UUID reviewId = upsertReview(userId, checkin, reviewImageUrls(photos));
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
     * The venue of a local event as the thing to verify against, when the event has no catalogue
     * place and is on right now (its times are stored in UTC). Null in every other case, which
     * leaves the ordinary place / ward ladder in charge: an event at a catalogue place is
     * verified against that place, and a post written before or after the event proves nothing
     * about having been at it.
     */
    private VerificationTarget eventVenueTarget(com.ds.goroute.localevent.domain.LocalEvent event, Place place) {
        if (event == null || place != null || event.getLatitude() == null || event.getLongitude() == null) {
            return null;
        }
        LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        boolean ongoing = event.getStartAt() != null && event.getEndAt() != null
                && !nowUtc.isBefore(event.getStartAt()) && !nowUtc.isAfter(event.getEndAt());
        if (!ongoing) {
            return null;
        }
        return VerificationTarget.forEventVenue(event.getLatitude(), event.getLongitude(),
                config.getInt(BusinessConfigKey.CHECKIN_EVENT_VERIFY_RADIUS_METERS));
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
        // A video already on this visit stays editable after the switch is turned off.
        Set<String> videosAlreadyPosted = checkinRepository.findPhotos(checkinId).stream()
                .filter(UserCheckinPhoto::isVideo)
                .map(UserCheckinPhoto::getUrl)
                .collect(Collectors.toSet());
        requireAllowedMedia(userId, photos, videosAlreadyPosted);
        requireAllowedMusic(userId, request.getMusicTrackId(), checkin.getMusicTrackId());

        checkin.setCaption(blankToNull(request.getCaption()));
        checkin.setFrameStyle(request.getFrameStyle());
        if (request.getPlaceKind() != null && !checkin.hasCataloguePlace()) {
            checkin.setPlaceKind(request.getPlaceKind());
        }
        checkin.setMusicTrackId(request.getMusicTrackId());
        if (request.getMuteVideoSound() != null) {
            checkin.setMuteVideoSound(request.getMuteVideoSound());
        }
        checkin.setCustomName(blankToNull(request.getCustomName()));
        checkin.setOverallRating(request.getOverallRating());
        checkin.setFoodRating(request.getFoodRating());
        checkin.setPriceRating(request.getPriceRating());
        checkin.setAmbianceRating(request.getAmbianceRating());
        checkin.setServiceRating(request.getServiceRating());
        checkin.setPhotoSource(photoSource);
        checkin.setVisibility(ContentVisibility.PUBLIC);
        if (request.getVisitedAt() != null) {
            checkin.setVisitedAt(resolveVisitedAt(request.getVisitedAt(), LocalDateTime.now()));
        }
        checkin.setEditedAt(LocalDateTime.now());
        checkin.setUpdatedAt(LocalDateTime.now());

        // A video keeps two files alive: the video and its still frame.
        List<String> keptUrls = photos.stream()
                .flatMap(photo -> Stream.of(photo.getUrl(), photo.getThumbnailUrl()))
                .filter(Objects::nonNull)
                .toList();

        // The review goes first so the cleanup below asks it what it shows *after* this
        // edit. Asked before, it would still be listing the photo the author just removed
        // and would keep the file alive for a post that no longer has it.
        if (checkin.hasRating() && checkin.hasCataloguePlace()) {
            checkin.setReviewId(upsertReview(userId, checkin, reviewImageUrls(photos)));
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
        remove(requireOwned(userId, checkinId), deleteReview);
    }

    @Override
    @Transactional
    public void removeAsAdmin(UUID checkinId, boolean deleteReview) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        remove(checkin, deleteReview);
    }

    private void remove(UserCheckin checkin, boolean deleteReview) {
        UUID userId = checkin.getUserId();
        UUID checkinId = checkin.getId();
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
    @Transactional(readOnly = true)
    public Map<UUID, UserCheckinResponse> findVisibleByIds(UUID viewerId, Collection<UUID> checkinIds) {
        if (checkinIds.isEmpty()) {
            return Map.of();
        }
        List<UserCheckin> live = checkinRepository.findByIds(checkinIds).stream()
                .filter(checkin -> checkin.getVisibility() == ContentVisibility.PUBLIC
                        && !Boolean.TRUE.equals(checkin.getIsRemoved()))
                .toList();
        Set<UUID> takenDown = contentModerationService.takenDownIds(ModeratedContentType.CHECKIN,
                live.stream().map(UserCheckin::getId).toList());
        List<UserCheckin> visible = live.stream()
                .filter(checkin -> !takenDown.contains(checkin.getId()))
                .toList();
        return toResponses(visible, Set.of(), viewerId).stream()
                .collect(Collectors.toMap(UserCheckinResponse::getId, response -> response));
    }

    @Override
    @Transactional
    public CheckinLikeResponse setLike(UUID userId, UUID checkinId, Boolean liked) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> found.getVisibility() == ContentVisibility.PUBLIC
                        && !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        if (contentModerationService.isTakenDown(ModeratedContentType.CHECKIN, checkinId)) {
            throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
        }
        boolean hasLiked = checkinRepository.hasLike(checkinId, userId);
        boolean wantLiked = liked == null ? !hasLiked : liked;
        if (hasLiked && !wantLiked) {
            checkinRepository.deleteLike(checkinId, userId);
        } else if (!hasLiked && wantLiked) {
            checkinRepository.insertLike(checkinId, userId);
            socialNotificationService.notifyLike(
                    checkin.getUserId(), userId, ModeratedContentType.CHECKIN.name(), checkinId);
        }
        return CheckinLikeResponse.builder()
                .checkinId(checkinId)
                .likeCount(checkinRepository.countLikes(checkinId))
                .hasLiked(wantLiked)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> feed(UUID viewerId, FeedGeoFilter geo, FeedPlaceKind kind,
                                          int page, int size, String cursor) {
        FeedCursor after = FeedCursor.decode(cursor);
        List<UserCheckin> checkins = checkinRepository.findFeed(viewerId, geo, kind, after,
                boundedSize(size), after != null ? 0 : boundedPage(page) * boundedSize(size));
        List<UserCheckinResponse> responses = toResponses(checkins, Set.of(), viewerId);
        // Same order as the rows, so each post carries the key the next page resumes after.
        for (int index = 0; index < responses.size(); index++) {
            UserCheckin checkin = checkins.get(index);
            responses.get(index).setFeedCursor(FeedCursor.encodeAfter(
                    checkin.getHasPhoto(), checkin.getCreatedAt(), checkin.getId()));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> followingFeed(UUID viewerId, Double focusLat, Double focusLng,
                                                   FeedPlaceKind kind, int page, int size) {
        boolean focused = focusLat != null && focusLng != null;
        List<UserCheckin> checkins = checkinRepository.findFollowingFeed(viewerId,
                focused ? focusLat : null, focused ? focusLng : null, kind,
                boundedSize(size), boundedPage(page) * boundedSize(size));
        List<UserCheckinResponse> responses = toResponses(checkins, Set.of(), viewerId);
        // The Friends tab keeps its order and marks what the viewer has not seen yet.
        Set<UUID> seen = feedImpressionService.seenIds(viewerId, FeedItemType.CHECKIN,
                checkins.stream().map(UserCheckin::getId).toList());
        // toResponses keeps the order, so row i is response i.
        for (int i = 0; i < responses.size(); i++) {
            responses.get(i).setFocusRank(checkins.get(i).getFocusRank());
            responses.get(i).setSeen(seen.contains(checkins.get(i).getId()));
        }
        return responses;
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
    public List<UserCheckinResponse> byPlace(UUID viewerId, UUID placeId, int page, int size) {
        List<UserCheckin> checkins = checkinRepository.findByPlace(
                placeId, boundedSize(size), boundedPage(page) * boundedSize(size));
        Set<UUID> latestReviewCheckinIds = checkinRepository.findLatestRatedPerUserForPlace(placeId).stream()
                .map(UserCheckin::getId)
                .collect(Collectors.toSet());
        // The viewer's own like/saved state rides on every row: a heart drawn empty for a
        // post the viewer already liked turns the next tap into an unlike.
        return toResponses(checkins, latestReviewCheckinIds, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byLocalEvent(UUID viewerId, UUID eventId, int page, int size) {
        return toResponses(checkinRepository.findByLocalEvent(
                eventId, boundedSize(size), boundedPage(page) * boundedSize(size)), Set.of(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byTrip(UUID viewerId, UUID tripId) {
        return toResponses(checkinRepository.findByTrip(tripId, MAX_TRIP_CHECKINS), Set.of(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byTripForMember(UUID userId, UUID tripId) {
        tripAccessGuard.requireAccess(tripId, userId);
        return byTrip(userId, tripId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCheckinResponse> byLocationKey(UUID viewerId, String locationKey, int page, int size) {
        return toResponses(checkinRepository.findByLocationKey(
                locationKey, boundedSize(size), boundedPage(page) * boundedSize(size)), Set.of(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitedWardResponse> visitedWards(UUID viewerId, UUID userId) {
        return checkinRepository.findVisitedWards(userId, userId.equals(viewerId));
    }

    @Override
    @Transactional
    public int convertLegacyReviews(int batchSize) {
        List<UUID> placeIds = checkinRepository.convertLegacyReviews(batchSize);
        // checkin_count of these places is recounted after the commit (PlaceUsageStatsJob).
        if (!placeIds.isEmpty()) {
            events.publishEvent(new PlaceUsageChangedEvent(
                    placeIds.stream().map(UUID::toString).collect(Collectors.toSet())));
        }
        return placeIds.size();
    }

    // --- rules -----------------------------------------------------------------------
    // Location verification lives in CheckinVerifier, shared with the operator flow that
    // attaches a place to an existing check-in.

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
                .locationVerified(checkin.getVerificationScope() == CheckinVerificationScope.PLACE)
                .weight(BigDecimal.ONE)
                .helpfulVotes(0)
                .unhelpfulVotes(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reviewRepository.save(review);
        return review.getId();
    }

    @Override
    public CheckinVideoUploadResponse uploadVideo(UUID userId, MultipartFile file) {
        requireFeatureEnabled();
        requireVideoAllowed(userId);
        // Checked before the upload so a too-long clip costs no storage. The length is read
        // from the file's own header; WEBM has none we read and is left to the app's limit.
        Integer durationSeconds = readDurationSeconds(file);
        requireVideoLength(durationSeconds);
        String url = fileUploadService.uploadVideo(VIDEO_PREFIX + userId, file);
        return CheckinVideoUploadResponse.builder()
                .url(url)
                .durationSeconds(durationSeconds)
                .build();
    }

    private Integer readDurationSeconds(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream input = file.getInputStream()) {
            return Mp4Duration.seconds(input);
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * A check-in is photos or exactly one video. The video must be one this user uploaded
     * through {@link #uploadVideo}, and carries the still frame the app cut from it.
     *
     * @param videosAlreadyPosted video URLs the check-in being edited already shows; those
     *                            stay allowed when the feature switch is off
     */
    private void requireAllowedMedia(UUID userId, List<CreateUserCheckinRequest.CheckinPhotoInput> photos,
                                     Set<String> videosAlreadyPosted) {
        List<CreateUserCheckinRequest.CheckinPhotoInput> videos = photos.stream()
                .filter(photo -> photo.getMediaType() == CheckinMediaType.VIDEO)
                .toList();
        if (videos.isEmpty()) {
            return;
        }
        if (photos.size() != 1) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A video check-in carries the video alone");
        }
        CreateUserCheckinRequest.CheckinPhotoInput video = videos.get(0);
        if (!videosAlreadyPosted.contains(video.getUrl())) {
            requireVideoAllowed(userId);
            if (!video.getUrl().contains(VIDEO_PREFIX + userId + "/")) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Upload the video through the check-in video upload first");
            }
        }
        if (video.getThumbnailUrl() == null || video.getThumbnailUrl().isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A video needs its still frame");
        }
        requireVideoLength(video.getDurationSeconds());
    }

    private void requireVideoLength(Integer durationSeconds) {
        int maxSeconds = config.getInt(BusinessConfigKey.CHECKIN_MAX_VIDEO_SECONDS);
        if (durationSeconds != null && durationSeconds > maxSeconds) {
            throw new BusinessException(ErrorConstant.CHECKIN_VIDEO_TOO_LONG,
                    "A check-in video can be at most " + maxSeconds + " seconds long");
        }
    }

    private void requireVideoAllowed(UUID userId) {
        if (!isVideoAllowed(userId)) {
            throw new BusinessException(ErrorConstant.CHECKIN_VIDEO_NOT_ALLOWED,
                    "Video check-ins are not available yet.");
        }
    }

    /** Open to everyone through CHECKIN.VIDEO_ENABLED; until then, beta testers and PRO members. */
    private boolean isVideoAllowed(UUID userId) {
        return config.getBoolean(BusinessConfigKey.CHECKIN_VIDEO_ENABLED)
                || betaAccess.isBetaUser(userId)
                || isPro(userId);
    }

    /**
     * A track must be in the active catalogue, and adding one needs the music switch (or beta).
     * Keeping the track the visit already has is always allowed, like a video after the switch
     * is turned off.
     */
    private void requireAllowedMusic(UUID userId, UUID trackId, UUID currentTrackId) {
        if (trackId == null || trackId.equals(currentTrackId)) {
            return;
        }
        if (!isMusicAllowed(userId)) {
            throw new BusinessException(ErrorConstant.CHECKIN_MUSIC_NOT_ALLOWED,
                    "Music on check-ins is not available yet.");
        }
        musicTrackService.requireActive(trackId);
    }

    private boolean isMusicAllowed(UUID userId) {
        return config.getBoolean(BusinessConfigKey.CHECKIN_MUSIC_ENABLED) || betaAccess.isBetaUser(userId);
    }

    /** What the linked review shows: the photos, or a video's still frame. */
    private List<String> reviewImageUrls(List<CreateUserCheckinRequest.CheckinPhotoInput> photos) {
        return photos.stream()
                .map(photo -> photo.getMediaType() == CheckinMediaType.VIDEO ? photo.getThumbnailUrl() : photo.getUrl())
                .filter(Objects::nonNull)
                .toList();
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
            boolean isVideo = photo.getMediaType() == CheckinMediaType.VIDEO;
            checkinRepository.insertPhoto(UserCheckinPhoto.builder()
                    .id(UUID.randomUUID())
                    .checkinId(checkinId)
                    .url(photo.getUrl())
                    .mediaType(isVideo ? CheckinMediaType.VIDEO : CheckinMediaType.IMAGE)
                    .thumbnailUrl(isVideo ? photo.getThumbnailUrl() : null)
                    .durationSeconds(isVideo ? photo.getDurationSeconds() : null)
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
        if (isPro(userId)) {
            return true;
        }
        return config.getBoolean(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE);
    }

    private boolean isPro(UUID userId) {
        return PRO_TIER.equalsIgnoreCase(subscriptionRepository.getSubscriptionTier(userId));
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
        // The linked reviews' photos for the whole page in one query, not one per review.
        Map<UUID, List<MediaAsset>> linkedReviewMedia = linkedReviews.isEmpty()
                ? Map.of()
                : mediaAssetRepository.findByEntityIds("USER_REVIEW", List.copyOf(linkedReviews.keySet()))
                        .stream()
                        .collect(Collectors.groupingBy(MediaAsset::getEntityId));
        Map<UUID, Place> places = loadPlaces(checkins);
        // The places' names in the reader's language, one query for the page.
        Map<UUID, List<com.ds.goroute.entity.PlaceTranslation>> placeTranslations =
                placeRepository.findTranslationsByPlaceIds(places.keySet());
        Map<UUID, Integer> commentCounts = commentCounts(checkins);

        List<UserCheckinResponse> responses = checkins.stream()
                .map(checkin -> toResponse(checkin, photos.getOrDefault(checkin.getId(), List.of()),
                        authors.getOrDefault(checkin.getUserId(), null), latestReviewCheckinIds.contains(checkin.getId()),
                        linkedReviews.get(checkin.getReviewId()),
                        checkin.getReviewId() == null ? List.of()
                                : linkedReviewMedia.getOrDefault(checkin.getReviewId(), List.of()),
                        likeCounts.getOrDefault(checkin.getId(), 0),
                        likedIds.contains(checkin.getId()),
                        checkin.getPlaceId() == null ? null : places.get(checkin.getPlaceId()),
                        checkin.getPlaceId() == null ? null : placeTranslations.get(checkin.getPlaceId()),
                        commentCounts.getOrDefault(checkin.getId(), 0)))
                .toList();
        markSaved(responses, viewerId);
        markGuides(responses);
        markMusic(checkins, responses);
        markEvents(checkins, responses);
        return responses;
    }

    /**
     * Names the local event of every check-in made at one, with one query for the page.
     * {@code responses} is in the order of {@code checkins}. A deleted event leaves the fields
     * null, so the post reads as an ordinary check-in at the venue.
     */
    private void markEvents(List<UserCheckin> checkins, List<UserCheckinResponse> responses) {
        List<UUID> eventIds = checkins.stream()
                .map(UserCheckin::getLocalEventId).filter(Objects::nonNull).distinct().toList();
        if (eventIds.isEmpty()) {
            return;
        }
        Map<UUID, com.ds.goroute.localevent.domain.LocalEvent> events =
                localEventRepository.findLiveSummariesByIds(eventIds);
        for (int index = 0; index < checkins.size(); index++) {
            com.ds.goroute.localevent.domain.LocalEvent event = events.get(checkins.get(index).getLocalEventId());
            if (event != null) {
                UserCheckinResponse response = responses.get(index);
                response.setLocalEventId(event.getId());
                response.setEventTitle(event.getTitle());
                response.setEventCoverUrl(event.getCoverUrl());
                response.setEventVenueName(event.getVenueName());
            }
        }
    }

    /**
     * Sets the background music of the whole page with one query. {@code responses} is in the
     * order of {@code checkins}. A track switched off in the catalogue is simply not shown.
     */
    private void markMusic(List<UserCheckin> checkins, List<UserCheckinResponse> responses) {
        List<UUID> trackIds = checkins.stream().map(UserCheckin::getMusicTrackId).filter(Objects::nonNull).toList();
        if (trackIds.isEmpty()) {
            return;
        }
        Map<UUID, MusicTrackResponse> tracks = musicTrackService.findActiveByIds(trackIds);
        for (int index = 0; index < checkins.size(); index++) {
            UUID trackId = checkins.get(index).getMusicTrackId();
            if (trackId != null) {
                responses.get(index).setMusic(tracks.get(trackId));
            }
        }
    }

    /**
     * Sets the guide badge of every author on the page with one query (user_guide_grants), so a
     * Local Guide's check-in carries the badge wherever it is listed.
     */
    private void markGuides(List<UserCheckinResponse> responses) {
        if (responses.isEmpty()) {
            return;
        }
        Map<UUID, String> guideTitles = userGuideService.activeGuideTitles(
                responses.stream().map(UserCheckinResponse::getUserId).toList());
        if (guideTitles.isEmpty()) {
            return;
        }
        responses.forEach(response -> {
            if (guideTitles.containsKey(response.getUserId())) {
                response.setUserGuide(true);
                response.setUserGuideTitle(guideTitles.get(response.getUserId()));
            }
        });
    }

    /** Sets {@code hasSaved} on the whole page with one query. Guests have saved nothing. */
    private void markSaved(List<UserCheckinResponse> responses, UUID viewerId) {
        if (viewerId == null || responses.isEmpty()) {
            return;
        }
        Set<UUID> savedIds = new java.util.HashSet<>(savedPostMapper.findSavedIds(viewerId, SavedPostType.CHECKIN,
                responses.stream().map(UserCheckinResponse::getId).toList()));
        responses.forEach(response -> response.setHasSaved(savedIds.contains(response.getId())));
    }

    /**
     * The comment count GET /content-comments/count would answer for each check-in, in one
     * grouped query. That endpoint only opens the thread of a public, live check-in that is
     * not taken down, so any other check-in counts 0 here; the takedown half is applied in
     * the query itself.
     */
    private Map<UUID, Integer> commentCounts(List<UserCheckin> checkins) {
        List<UUID> commentable = checkins.stream()
                .filter(UserCheckinServiceImpl::hasCommentThread)
                .map(UserCheckin::getId)
                .toList();
        return commentable.isEmpty()
                ? Map.of()
                : contentCommentRepository.countActiveByTargets(ModeratedContentType.CHECKIN, commentable);
    }

    private static boolean hasCommentThread(UserCheckin checkin) {
        return checkin.getVisibility() == ContentVisibility.PUBLIC && !Boolean.TRUE.equals(checkin.getIsRemoved());
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
        return placeRepository.findCardsByIds(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (first, second) -> first));
    }

    /** One query for every author on the page. */
    private Map<UUID, User> loadAuthors(List<UserCheckin> checkins) {
        Set<UUID> authorIds = checkins.stream()
                .map(UserCheckin::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return userRepository.findByIds(authorIds).stream()
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
        List<MediaAsset> linkedReviewMedia = linkedReview == null ? List.of()
                : mediaAssetRepository.findByEntity("USER_REVIEW", linkedReview.getId());
        UserCheckinResponse response = toResponse(checkin, photos,
                userRepository.findById(checkin.getUserId()).orElse(null), false,
                linkedReview, linkedReviewMedia, checkinRepository.countLikes(checkin.getId()),
                viewerId != null && checkinRepository.hasLike(checkin.getId(), viewerId), place,
                place == null ? null
                        : placeRepository.findTranslationsByPlaceIds(List.of(place.getId())).get(place.getId()),
                commentCounts(List.of(checkin)).getOrDefault(checkin.getId(), 0));
        markSaved(List.of(response), viewerId);
        markGuides(List.of(response));
        markMusic(List.of(checkin), List.of(response));
        markEvents(List.of(checkin), List.of(response));
        return response;
    }

    private UserCheckinResponse toResponse(UserCheckin checkin, List<UserCheckinPhoto> photos, User author,
                                           boolean latestReview, UserReview linkedReview,
                                           List<MediaAsset> linkedReviewMedia, int likeCount,
                                           boolean hasLiked, Place place,
                                           List<com.ds.goroute.entity.PlaceTranslation> placeTranslations,
                                           int commentCount) {
        // The linked place in the reader's language, written into the fields every client
        // already shows. The location name was copied from the place when the author picked
        // it, so it follows the place's name while it is still one of its names; a name the
        // author or the map gave it stays as written.
        String language = com.ds.goroute.config.filter.AcceptLanguageFilter.currentCode();
        String localizedPlaceName = com.ds.goroute.service.PlaceTranslationService.localizedName(
                placeTranslations, language);
        String localizedLocationName = place == null ? null
                : com.ds.goroute.service.PlaceTranslationService.localizedCopy(
                        checkin.getLocationName(), place.getTitle(), placeTranslations, language);
        String locationName = localizedLocationName != null ? localizedLocationName : checkin.getLocationName();
        List<MemoryImageResponse> photoResponses = photos.stream()
                .sorted(Comparator.comparing(UserCheckinPhoto::getPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(photo -> MemoryImageResponse.builder()
                        .id(photo.getId())
                        .url(photo.getUrl())
                        .entityType("USER_CHECKIN")
                        .entityId(checkin.getId())
                        .mediaType(photo.isVideo() ? CheckinMediaType.VIDEO.name() : CheckinMediaType.IMAGE.name())
                        .thumbnailUrl(photo.getThumbnailUrl())
                        .durationSeconds(photo.getDurationSeconds())
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
                        .locationName(locationName)
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
                .userUsername(author == null ? null : author.getUsername())
                .userAvatarUrl(author == null ? null : author.getAvatarUrl())
                .tripId(checkin.getTripId())
                .activityId(checkin.getActivityId())
                .placeId(checkin.getPlaceId())
                .reviewId(checkin.getReviewId())
                .locationName(locationName)
                .customName(checkin.getCustomName())
                .latitude(checkin.getLatitude())
                .longitude(checkin.getLongitude())
                .ward(checkin.getWard())
                .district(checkin.getDistrict())
                .province(checkin.getProvince())
                .provinceCode(checkin.getProvinceCode())
                .locationSource(checkin.getLocationSource())
                .locationKey(checkin.getLocationKey())
                .placeName(place == null ? null : localizedPlaceName != null ? localizedPlaceName : place.getTitle())
                .placeAddress(place == null ? null : place.getAddress())
                .placeThumbnail(place == null ? null : place.getThumbnail())
                .placeReviewCount(place == null ? null : place.getReviewCount())
                .placeReviewRating(place == null ? null : place.getReviewRating())
                .placeAdjustedRating(PlaceDisplayRating.of(place))
                .placeLatitude(place == null ? null : place.getLatitude())
                .placeLongitude(place == null ? null : place.getLongitude())
                .caption(checkin.getCaption())
                .frameStyle(checkin.getFrameStyle())
                .placeKind(checkin.getPlaceKind())
                .muteVideoSound(checkin.isMuteVideoSound())
                .overallRating(checkin.getOverallRating())
                .foodRating(checkin.getFoodRating())
                .priceRating(checkin.getPriceRating())
                .ambianceRating(checkin.getAmbianceRating())
                .serviceRating(checkin.getServiceRating())
                .photoSource(checkin.getPhotoSource())
                .visibility(checkin.getVisibility())
                .verificationStatus(checkin.getVerificationStatus())
                .verificationScope(checkin.getVerificationScope())
                .wardCode(checkin.getWardCode())
                .distanceMeters(checkin.getDistanceMeters())
                .rewardPoints(checkin.getRewardPoints())
                .rewardReason(checkin.getRewardReason())
                .rewardReasonCodes(CheckinRewardCalculator.parseReasonCodes(checkin.getRewardReason()))
                .edited(checkin.getEditedAt() != null)
                .visitedAt(checkin.getVisitedAt() != null ? checkin.getVisitedAt() : checkin.getCreatedAt())
                .createdAt(checkin.getCreatedAt())
                .linkedToReview(checkin.getReviewId() != null)
                .latestReview(latestReview)
                .linkedReview(toLinkedReview(linkedReview, linkedReviewMedia))
                .likeCount(likeCount)
                .hasLiked(hasLiked)
                .commentCount(commentCount)
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
                                .mediaType(photo.isVideo() ? CheckinMediaType.VIDEO : CheckinMediaType.IMAGE)
                                .thumbnailUrl(photo.getThumbnailUrl())
                                .durationSeconds(photo.getDurationSeconds())
                                .build())
                        .toList())
                .photosV2(photoResponses)
                .build();
    }

    private CheckinLinkedReviewResponse toLinkedReview(UserReview review, List<MediaAsset> media) {
        if (review == null) {
            return null;
        }
        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(media);
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

    /**
     * The visit date the author asked for, or now when they sent none.
     *
     * <p>A day of slack forward absorbs a phone running ahead of the server; beyond that it
     * is not a day anybody has been anywhere yet. Ten years back is the other end: a check-in
     * older than the product is a typo or a probe, and either way it would sit at the bottom
     * of a history nobody can scroll to.
     */
    private LocalDateTime resolveVisitedAt(LocalDateTime requested, LocalDateTime now) {
        if (requested == null) {
            return now;
        }
        if (requested.isAfter(now.plusDays(1))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A visit date cannot be in the future");
        }
        if (requested.isBefore(now.minusYears(10))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A visit date cannot be more than 10 years ago");
        }
        return requested;
    }
}
