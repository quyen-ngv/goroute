package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.CheckinLocationSnapshot;
import com.ds.goroute.dto.request.AssignCheckinPlaceRequest;
import com.ds.goroute.dto.request.HideCheckinRequest;
import com.ds.goroute.dto.response.AdminCheckinResponse;
import com.ds.goroute.dto.response.CheckinLocationHistoryResponse;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinLocationHistory;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.CheckinAdminService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.checkin.CheckinReviewConverter;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.ErrorMessages;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class CheckinAdminServiceImpl implements CheckinAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserCheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final ModerationFlagRepository flagRepository;
    private final ContentModerationService contentModerationService;
    private final NotificationService notificationService;
    private final CheckinReviewConverter reviewConverter;
    private final ReviewService reviewService;
    private final ReviewScoringService scoringService;
    private final LocationKeyFactory locationKeyFactory;
    private final PassportService passportService;

    @Override
    @Transactional(readOnly = true)
    public List<AdminCheckinResponse> list(String search, UUID userId, Boolean hidden, int page, int size) {
        int boundedSize = boundedSize(size);
        List<UserCheckin> checkins = checkinRepository.findAllForAdmin(
                blankToNull(search), userId, hidden, boundedSize, boundedPage(page) * boundedSize);
        return toResponses(checkins);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(String search, UUID userId, Boolean hidden) {
        return checkinRepository.countAllForAdmin(blankToNull(search), userId, hidden);
    }

    @Override
    @Transactional
    public void hide(UUID adminId, UUID checkinId, HideCheckinRequest request) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        flagRepository.insertTakedown(ContentTakedown.builder()
                .id(UUID.randomUUID())
                .contentType(ModeratedContentType.CHECKIN)
                .contentId(checkinId)
                .ownerId(checkin.getUserId())
                .category(request.getCategory())
                .reason(request.getReason())
                .removedBy(adminId)
                .removedAt(LocalDateTime.now())
                .build());
        notifyOwner(checkin, adminId, request.getReason());
    }

    @Override
    @Transactional
    public void show(UUID adminId, UUID checkinId) {
        checkinRepository.findById(checkinId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        flagRepository.restoreTakedown(ModeratedContentType.CHECKIN.name(), checkinId, adminId);
    }

    @Override
    @Transactional
    public void assignPlace(UUID adminId, UUID checkinId, AssignCheckinPlaceRequest request) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));
        UUID previousPlaceId = checkin.getPlaceId();
        if (place.getId().equals(previousPlaceId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "This check-in is already linked to that place");
        }
        UUID previousReviewId = checkin.getReviewId();

        // Written before anything is overwritten: once the catalogue row is attached, the
        // author's own account of where they were is the only evidence that the operator
        // read the situation correctly.
        snapshotLocation(checkin, place.getId(), adminId, blankToNull(request.getReason()));

        String locationKey = locationKeyFactory.forPlace(place.getId());
        if (checkinRepository.assignPlace(checkinId, place.getId(), locationKey, place.getProvinceCode()) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
        checkin.setPlaceId(place.getId());
        checkin.setLocationKey(locationKey);
        if (place.getProvinceCode() != null) {
            checkin.setProvinceCode(place.getProvinceCode());
        }

        moveRating(checkin, previousPlaceId, previousReviewId);
        // The province a check-in resolves to usually changes the moment it gains a place,
        // so the passport entry has to be re-pointed or the author silently keeps an entry
        // that proves a visit to nowhere.
        passportService.reprojectCheckinPlace(checkin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckinLocationHistoryResponse> locationHistory(UUID checkinId) {
        List<UserCheckinLocationHistory> history = checkinRepository.findLocationHistory(checkinId);
        if (history.isEmpty()) {
            return List.of();
        }
        Map<UUID, Place> places = placeRepository.findByIds(history.stream()
                        .flatMap(row -> Stream.of(row.getPreviousPlaceId(), row.getNewPlaceId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (first, second) -> first));

        return history.stream().map(row -> {
            CheckinLocationSnapshot previous = JsonUtils.fromJson(
                    row.getPreviousLocation(), CheckinLocationSnapshot.class);
            Place previousPlace = row.getPreviousPlaceId() == null ? null : places.get(row.getPreviousPlaceId());
            Place newPlace = places.get(row.getNewPlaceId());
            return CheckinLocationHistoryResponse.builder()
                    .id(row.getId())
                    .previousPlaceId(row.getPreviousPlaceId())
                    .previousPlaceName(previousPlace == null ? null : previousPlace.getTitle())
                    .newPlaceId(row.getNewPlaceId())
                    .newPlaceName(newPlace == null ? null : newPlace.getTitle())
                    .previousLocationName(previous == null ? null : previous.locationName())
                    .previousCustomName(previous == null ? null : previous.customName())
                    .previousLatitude(previous == null ? null : previous.latitude())
                    .previousLongitude(previous == null ? null : previous.longitude())
                    .previousWard(previous == null ? null : previous.ward())
                    .previousDistrict(previous == null ? null : previous.district())
                    .previousProvince(previous == null ? null : previous.province())
                    .previousProvinceCode(previous == null ? null : previous.provinceCode())
                    .previousLocationSource(previous == null ? null : previous.locationSource())
                    .previousLocationKey(previous == null ? null : previous.locationKey())
                    .reason(row.getReason())
                    .changedBy(row.getChangedBy())
                    .changedByName(row.getChangedBy() == null ? null
                            : userRepository.findById(row.getChangedBy()).map(User::getFullName).orElse(null))
                    .changedAt(row.getChangedAt())
                    .build();
        }).toList();
    }

    private void snapshotLocation(UserCheckin checkin, UUID newPlaceId, UUID adminId, String reason) {
        checkinRepository.insertLocationHistory(UserCheckinLocationHistory.builder()
                .id(UUID.randomUUID())
                .checkinId(checkin.getId())
                .previousPlaceId(checkin.getPlaceId())
                .newPlaceId(newPlaceId)
                .previousLocation(JsonUtils.toJson(new CheckinLocationSnapshot(
                        checkin.getPlaceId(),
                        checkin.getLocationName(),
                        checkin.getCustomName(),
                        checkin.getLatitude(),
                        checkin.getLongitude(),
                        checkin.getWard(),
                        checkin.getDistrict(),
                        checkin.getProvince(),
                        checkin.getProvinceCode(),
                        checkin.getLocationSource() == null ? null : checkin.getLocationSource().name(),
                        checkin.getLocationKey())))
                .reason(reason)
                .changedBy(adminId)
                .changedAt(LocalDateTime.now())
                .build());
    }

    /**
     * The score the author gave is about the spot, so it follows the check-in to the place
     * it turned out to be -- the same conversion a cluster promotion does.
     */
    private void moveRating(UserCheckin checkin, UUID previousPlaceId, UUID previousReviewId) {
        if (checkin.hasRating()) {
            List<String> photoUrls = checkinRepository.findPhotos(checkin.getId()).stream()
                    .map(UserCheckinPhoto::getUrl)
                    .toList();
            UUID reviewId = reviewConverter.convert(checkin, checkin.getPlaceId(), photoUrls);
            checkinRepository.attachReview(checkin.getId(), reviewId);
            checkin.setReviewId(reviewId);
            scoringService.recalculatePlaceScores(checkin.getPlaceId());
        }
        dropReviewLeftBehind(checkin, previousPlaceId, previousReviewId);
    }

    /**
     * Removes the review the author now turns out never to have earned -- but only when this
     * check-in was the last visit of theirs at the old place. Another visit still there means
     * the opinion keeps an author behind it, and deleting it would destroy a second memory to
     * correct the first.
     */
    private void dropReviewLeftBehind(UserCheckin checkin, UUID previousPlaceId, UUID previousReviewId) {
        if (previousPlaceId == null || previousReviewId == null) {
            return;
        }
        // Counted after the move, so this check-in no longer counts as a visit to the old place.
        if (checkinRepository.countByUserAndPlace(checkin.getUserId(), previousPlaceId) == 0) {
            // Deleting the review also cuts the link from every check-in that pointed at it.
            reviewService.deleteReview(checkin.getUserId(), previousReviewId);
            checkin.setReviewId(null);
            return;
        }
        // The review survives on the strength of another visit, but this check-in is no
        // longer the evidence for it: a visit somewhere else cannot keep pointing at it.
        if (previousReviewId.equals(checkin.getReviewId())) {
            checkinRepository.attachReview(checkin.getId(), null);
            checkin.setReviewId(null);
        }
    }

    /** A silent hide creates a support ticket; a failed notification must not undo it either. */
    private void notifyOwner(UserCheckin checkin, UUID adminId, String reason) {
        try {
            notificationService.createNotification(
                    checkin.getUserId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    ErrorMessages.of(ErrorConstant.CONTENT_TAKEN_DOWN),
                    reason,
                    Map.of("contentType", ModeratedContentType.CHECKIN.name(),
                            "contentId", checkin.getId().toString()),
                    adminId);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a checkin takedown: {}",
                    checkin.getUserId(), exception.getMessage(), exception);
        }
    }

    private List<AdminCheckinResponse> toResponses(List<UserCheckin> checkins) {
        if (checkins.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = checkins.stream().map(UserCheckin::getId).toList();
        Set<UUID> hiddenIds = contentModerationService.takenDownIds(ModeratedContentType.CHECKIN, ids);
        Map<UUID, User> authors = loadAuthors(checkins);
        Map<UUID, Place> places = loadPlaces(checkins);
        Map<UUID, List<String>> photosByCheckin = checkinRepository.findPhotosForCheckins(ids).stream()
                .collect(Collectors.groupingBy(UserCheckinPhoto::getCheckinId,
                        Collectors.collectingAndThen(Collectors.toList(), photos -> photos.stream()
                                .sorted(Comparator.comparing(UserCheckinPhoto::getPosition,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(UserCheckinPhoto::getUrl)
                                .toList())));
        Map<UUID, Integer> likeCounts = checkinRepository.findLikeCounts(ids).stream()
                .collect(Collectors.toMap(count -> count.getCheckinId(),
                        count -> count.getLikeCount() == null ? 0 : count.getLikeCount()));
        Set<UUID> reassignedIds = new java.util.HashSet<>(checkinRepository.findReassignedCheckinIds(ids));

        return checkins.stream()
                .map(checkin -> {
                    User author = authors.get(checkin.getUserId());
                    Place place = checkin.getPlaceId() == null ? null : places.get(checkin.getPlaceId());
                    return AdminCheckinResponse.builder()
                            .id(checkin.getId())
                            .userId(checkin.getUserId())
                            .userDisplayName(author == null ? null : author.getFullName())
                            .userEmail(author == null ? null : author.getEmail())
                            .userAvatarUrl(author == null ? null : author.getAvatarUrl())
                            .placeId(checkin.getPlaceId())
                            .placeName(place == null ? null : place.getTitle())
                            .placeAddress(place == null ? null : place.getAddress())
                            .locationName(checkin.getLocationName())
                            .customName(checkin.getCustomName())
                            .ward(checkin.getWard())
                            .district(checkin.getDistrict())
                            .province(checkin.getProvince())
                            .provinceCode(checkin.getProvinceCode())
                            .latitude(checkin.getLatitude())
                            .longitude(checkin.getLongitude())
                            .locationSource(checkin.getLocationSource())
                            .locationKey(checkin.getLocationKey())
                            .locationReassigned(reassignedIds.contains(checkin.getId()))
                            .caption(checkin.getCaption())
                            .photoUrls(photosByCheckin.getOrDefault(checkin.getId(), List.of()))
                            .overallRating(checkin.getOverallRating())
                            .visibility(checkin.getVisibility())
                            .likeCount(likeCounts.getOrDefault(checkin.getId(), 0))
                            .hidden(hiddenIds.contains(checkin.getId()))
                            .createdAt(checkin.getCreatedAt())
                            .build();
                })
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
