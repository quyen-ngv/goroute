package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssignActivityPlaceRequest;
import com.ds.goroute.dto.response.AdminUnlinkedActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.UnlinkedActivityRow;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityPlaceAdminService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.redis.RedisService;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityPlaceAdminServiceImpl implements ActivityPlaceAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final TripRealtimePublisher tripRealtimePublisher;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUnlinkedActivityResponse> listUnlinked(String search, boolean dismissed, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<UnlinkedActivityRow> rows = activityRepository.findUnlinkedForAdmin(
                blankToNull(search), dismissed, boundedSize, Math.max(0, page) * boundedSize);
        if (rows.isEmpty()) {
            return List.of();
        }
        // Trip owners and the operators who dismissed rows, in one lookup.
        Map<UUID, User> users = userRepository.findByIds(rows.stream()
                        .flatMap(row -> Stream.of(row.getOwnerId(), row.getDismissedBy()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));
        return rows.stream().map(row -> {
            User owner = users.get(row.getOwnerId());
            User dismisser = row.getDismissedBy() == null ? null : users.get(row.getDismissedBy());
            return AdminUnlinkedActivityResponse.builder()
                    .id(row.getId())
                    .tripId(row.getTripId())
                    .tripName(row.getTripName())
                    .ownerId(row.getOwnerId())
                    .ownerDisplayName(owner == null ? null : owner.getFullName())
                    .ownerEmail(owner == null ? null : owner.getEmail())
                    .dayNumber(row.getDayNumber())
                    .name(row.getName())
                    .address(row.getAddress())
                    .lat(row.getLat())
                    .lng(row.getLng())
                    .category(row.getCategory())
                    .placeId(row.getPlaceId())
                    .customPlaceId(row.getCustomPlaceId())
                    .createdAt(row.getCreatedAt())
                    .dismissedAt(row.getDismissedAt())
                    .dismissedBy(row.getDismissedBy())
                    .dismissedByName(dismisser == null ? null : dismisser.getFullName())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnlinked(String search, boolean dismissed) {
        return activityRepository.countUnlinkedForAdmin(blankToNull(search), dismissed);
    }

    @Override
    @Transactional
    public void dismiss(UUID adminId, UUID activityId) {
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        // Another operator may have dismissed it since the queue loaded.
        if (activityRepository.dismissPlaceReview(activityId, adminId) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This activity is already out of the queue");
        }
        log.info("Admin {} dismissed activity {} from the unlinked queue", adminId, activityId);
    }

    @Override
    @Transactional
    public void restore(UUID adminId, UUID activityId) {
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        if (activityRepository.restorePlaceReview(activityId) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This activity is already in the queue");
        }
        log.info("Admin {} restored activity {} to the unlinked queue", adminId, activityId);
    }

    @Override
    @Transactional
    public void assignPlace(UUID adminId, UUID activityId, AssignActivityPlaceRequest request) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        tripRepository.findById(activity.getTripId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        // Another operator, or the trip's own members, may have linked it since the queue loaded.
        if (isLinked(activity)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This activity is already linked to a catalogue place");
        }
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));

        // The activity keeps its own name and address: the response already shows the place's
        // address once linked, and the user's text stays as the record of what they typed.
        if (activityRepository.assignPlace(activityId, place.getId(), place.getLatitude(), place.getLongitude()) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }
        redisService.delete("activities:" + activity.getTripId());
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_UPDATED, activity.getTripId(), activityId, adminId);
        log.info("Admin {} linked activity {} to place {}", adminId, activityId, place.getId());
    }

    private boolean isLinked(Activity activity) {
        List<UUID> candidates = Stream.of(activity.getPlaceRefId(), parseUuid(activity.getPlaceId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return !candidates.isEmpty() && !placeRepository.findByIds(candidates).isEmpty();
    }

    private UUID parseUuid(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
