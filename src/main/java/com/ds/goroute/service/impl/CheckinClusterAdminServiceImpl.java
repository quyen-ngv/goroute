package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.PromoteCheckinClusterRequest;
import com.ds.goroute.dto.response.CheckinClusterResponse;
import com.ds.goroute.entity.CheckinCluster;
import com.ds.goroute.entity.CheckinClusterDecision;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.CheckinClusterAdminService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinClusterDecisionStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinClusterAdminServiceImpl implements CheckinClusterAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Namespace for catalogue rows that came from a cluster. The external identifier column
     * already holds Google identifiers, so anything else needs its own prefix to stay
     * distinguishable -- and being able to tell where a row came from is the whole reason
     * the promotion path exists rather than a direct user write.
     */
    private static final String CLUSTER_PLACE_NAMESPACE = "goroute:cluster:";

    private final UserCheckinRepository checkinRepository;
    private final UserReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final ReviewScoringService scoringService;
    private final NotificationService notificationService;
    private final BusinessConfigService config;

    @Override
    @Transactional(readOnly = true)
    public List<CheckinClusterResponse> queue(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return checkinRepository.findClusters(minUsers(), minCheckins(), safeSize,
                        Math.max(0, page) * safeSize).stream()
                .map(cluster -> toResponse(cluster, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countQueue() {
        return checkinRepository.countClusters(minUsers(), minCheckins());
    }

    @Override
    @Transactional(readOnly = true)
    public CheckinClusterResponse get(String locationKey) {
        CheckinCluster cluster = requireCluster(locationKey);
        return toResponse(cluster, checkinRepository.findClusterDecision(locationKey).orElse(null));
    }

    @Override
    @Transactional
    public CheckinClusterResponse promote(UUID operatorId, String locationKey,
                                          PromoteCheckinClusterRequest request) {
        CheckinCluster cluster = requireCluster(locationKey);

        Place place = Place.builder()
                .id(UUID.randomUUID())
                .placeId(CLUSTER_PLACE_NAMESPACE + locationKey)
                .title(request.getTitle().trim())
                .address(request.getAddress())
                .placeGroup(request.getPlaceGroup() == null ? PlaceGroup.OTHER : request.getPlaceGroup())
                .latitude(request.getLatitude() == null ? cluster.getCentroidLatitude() : request.getLatitude())
                .longitude(request.getLongitude() == null ? cluster.getCentroidLongitude() : request.getLongitude())
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .reviewCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        placeRepository.insert(place);

        attachClusterToPlace(locationKey, place.getId());
        recordDecision(locationKey, CheckinClusterDecisionStatus.PROMOTED, place.getId(),
                operatorId, request.getNote(), cluster.getCheckinCount());

        return toResponse(requireCluster(locationKey),
                checkinRepository.findClusterDecision(locationKey).orElse(null));
    }

    @Override
    @Transactional
    public CheckinClusterResponse merge(UUID operatorId, String locationKey, UUID placeId) {
        CheckinCluster cluster = requireCluster(locationKey);
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));

        attachClusterToPlace(locationKey, placeId);
        recordDecision(locationKey, CheckinClusterDecisionStatus.MERGED, placeId, operatorId,
                null, cluster.getCheckinCount());

        return toResponse(requireCluster(locationKey),
                checkinRepository.findClusterDecision(locationKey).orElse(null));
    }

    @Override
    @Transactional
    public void ignore(UUID operatorId, String locationKey, String note) {
        CheckinCluster cluster = requireCluster(locationKey);
        // The count at the time of the decision is stored so the cluster can come back if
        // it grows: "not worth it at five visits" is not the same judgement as at fifty.
        recordDecision(locationKey, CheckinClusterDecisionStatus.IGNORED, null, operatorId,
                note, cluster.getCheckinCount());
    }

    @Override
    @Transactional
    public void revertPromotion(UUID operatorId, String locationKey) {
        CheckinClusterDecision decision = checkinRepository.findClusterDecision(locationKey)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "No decision to undo"));
        if (decision.getStatus() != CheckinClusterDecisionStatus.PROMOTED) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only a promotion can be undone");
        }
        checkinRepository.deleteClusterDecision(locationKey);
        log.info("Operator {} undid the promotion of cluster {} (place {})",
                operatorId, locationKey, decision.getPlaceId());
    }

    /**
     * Attaches every check-in in the cluster to the place and converts the scores people
     * already gave into real reviews.
     *
     * <p>One review per person: where somebody rated the spot several times, the most
     * recent score wins, because that is the opinion they currently hold. The earlier ones
     * stay on their own check-ins as part of those memories.
     */
    private void attachClusterToPlace(String locationKey, UUID placeId) {
        checkinRepository.attachPlaceToCluster(locationKey, placeId);

        for (UserCheckin rated : checkinRepository.findLatestRatedPerUserForLocationKey(locationKey)) {
            List<String> photoUrls = checkinRepository.findPhotos(rated.getId()).stream()
                    .map(photo -> photo.getUrl())
                    .toList();
            UUID reviewId = convertToReview(rated, placeId, photoUrls);
            checkinRepository.attachReview(rated.getId(), reviewId);
            notifyRatingIsNowPublic(rated, placeId);
        }
        scoringService.recalculatePlaceScores(placeId);
    }

    private UUID convertToReview(UserCheckin checkin, UUID placeId, List<String> photoUrls) {
        Optional<UserReview> existing = reviewRepository.findByUserAndPlace(checkin.getUserId(), placeId);
        LocalDateTime now = LocalDateTime.now();
        String reviewPhotos = photoUrls.isEmpty() ? null : JsonUtils.toJson(photoUrls);

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

    /**
     * Tells the author their score is now visible to other people.
     *
     * <p>Required rather than polite: when they gave it, the spot had no page and the score
     * counted towards nothing. That changed without them doing anything, and finding out by
     * accident is worse than a slightly awkward notification.
     */
    private void notifyRatingIsNowPublic(UserCheckin checkin, UUID placeId) {
        try {
            notificationService.createNotification(
                    checkin.getUserId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    "Đánh giá của bạn giờ đã hiển thị công khai",
                    "Nơi bạn từng check-in và chấm sao vừa được đưa vào danh mục địa điểm, "
                            + "nên đánh giá của bạn giờ hiển thị trên trang địa điểm đó.",
                    Map.of("placeId", placeId.toString(), "checkinId", checkin.getId().toString()),
                    null);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} that their rating became public: {}",
                    checkin.getUserId(), exception.getMessage(), exception);
        }
    }

    private void recordDecision(String locationKey, CheckinClusterDecisionStatus status, UUID placeId,
                                UUID operatorId, String note, Integer checkinCount) {
        checkinRepository.upsertClusterDecision(CheckinClusterDecision.builder()
                .locationKey(locationKey)
                .status(status)
                .placeId(placeId)
                .decidedBy(operatorId)
                .decidedAt(LocalDateTime.now())
                .note(note)
                .checkinCountAtDecision(checkinCount == null ? 0 : checkinCount)
                .build());
    }

    private CheckinCluster requireCluster(String locationKey) {
        return checkinRepository.findCluster(locationKey)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Cluster not found"));
    }

    private CheckinClusterResponse toResponse(CheckinCluster cluster, CheckinClusterDecision decision) {
        return CheckinClusterResponse.builder()
                .locationKey(cluster.getLocationKey())
                .commonName(cluster.getCommonName())
                .alternateNames(split(cluster.getAlternateNames(), " \\| "))
                .centroidLatitude(cluster.getCentroidLatitude())
                .centroidLongitude(cluster.getCentroidLongitude())
                .ward(cluster.getWard())
                .district(cluster.getDistrict())
                .province(cluster.getProvince())
                .provinceCode(cluster.getProvinceCode())
                .checkinCount(orZero(cluster.getCheckinCount()))
                .distinctUserCount(orZero(cluster.getDistinctUserCount()))
                .ratedCheckinCount(orZero(cluster.getRatedCheckinCount()))
                .firstSeenAt(cluster.getFirstSeenAt())
                .lastSeenAt(cluster.getLastSeenAt())
                .samplePhotos(split(cluster.getSamplePhotos(), ","))
                .decisionStatus(decision == null ? null : decision.getStatus())
                .decisionPlaceId(decision == null ? null : decision.getPlaceId())
                .build();
    }

    private List<String> split(String value, String separator) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(separator))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int minUsers() {
        return config.getInt(BusinessConfigKey.CHECKIN_CLUSTER_MIN_USERS);
    }

    private int minCheckins() {
        return config.getInt(BusinessConfigKey.CHECKIN_CLUSTER_MIN_CHECKINS);
    }
}
