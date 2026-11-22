package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.TripModerationRequest;
import com.ds.goroute.dto.response.AdminPublicTripResponse;
import com.ds.goroute.entity.AdminTripRow;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.TripAdminService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.trip.PublicTripRankingCache;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.TripVisibility;
import com.ds.goroute.utils.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripAdminServiceImpl implements TripAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String UNPUBLISHED_TITLE_KEY = "moderation.trip.unpublished";

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ModerationFlagRepository flagRepository;
    private final NotificationService notificationService;
    private final PublicTripRankingCache publicTripRankingCache;
    private final TripRealtimePublisher tripRealtimePublisher;

    @Override
    @Transactional(readOnly = true)
    public List<AdminPublicTripResponse> list(String search, UUID ownerId, TripVisibility visibility, Boolean hidden,
                                              int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<AdminTripRow> rows = tripRepository.findAdminSharedTrips(blankToNull(search), ownerId,
                sharedVisibility(visibility), hidden, boundedSize, Math.max(0, page) * boundedSize);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> owners = userRepository.findByIds(rows.stream().map(AdminTripRow::getOwnerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        return rows.stream().map(row -> toResponse(row, owners.get(row.getOwnerId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(String search, UUID ownerId, TripVisibility visibility, Boolean hidden) {
        return tripRepository.countAdminSharedTrips(blankToNull(search), ownerId, sharedVisibility(visibility), hidden);
    }

    @Override
    @Transactional
    public void hide(UUID adminId, UUID tripId, TripModerationRequest request) {
        Trip trip = requireTrip(tripId);
        flagRepository.insertTakedown(ContentTakedown.builder()
                .id(UUID.randomUUID())
                .contentType(ModeratedContentType.TRIP)
                .contentId(tripId)
                .ownerId(trip.getOwnerId())
                .category(request.getCategory())
                .reason(request.getReason())
                .removedBy(adminId)
                .removedAt(LocalDateTime.now())
                .build());
        // The cached ranking is re-checked against takedowns when read; dropping it keeps pages full.
        publicTripRankingCache.invalidateAllAfterCommit();
        notifyOwner(trip, adminId, ErrorMessages.of(ErrorConstant.CONTENT_TAKEN_DOWN), request.getReason());
    }

    @Override
    @Transactional
    public void show(UUID adminId, UUID tripId) {
        requireTrip(tripId);
        flagRepository.restoreTakedown(ModeratedContentType.TRIP.name(), tripId, adminId);
        publicTripRankingCache.invalidateAllAfterCommit();
    }

    @Override
    @Transactional
    public void unpublish(UUID adminId, UUID tripId, TripModerationRequest request) {
        Trip trip = requireTrip(tripId);
        if (trip.getVisibility() == null || trip.getVisibility() == TripVisibility.PRIVATE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Trip is not shared");
        }
        trip.setVisibility(TripVisibility.PRIVATE);
        tripRepository.updateById(trip);
        publicTripRankingCache.invalidateAllAfterCommit();
        // Members with the trip open pick up the new visibility like any other edit.
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.TRIP_UPDATED, tripId, tripId, adminId,
                Map.of("fields", List.of("visibility")));
        log.info("Trip {} set to private by admin {}", tripId, adminId);
        notifyOwner(trip, adminId, ErrorMessages.of(UNPUBLISHED_TITLE_KEY), request.getReason());
    }

    private Trip requireTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
    }

    /** Only the two shared values filter; PRIVATE trips are never listed here. */
    private static String sharedVisibility(TripVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        if (visibility == TripVisibility.PRIVATE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "visibility must be PUBLIC or SHARED");
        }
        return visibility.name();
    }

    /** A silent takedown creates a support ticket; a failed notification must not undo it either. */
    private void notifyOwner(Trip trip, UUID adminId, String title, String reason) {
        try {
            notificationService.createNotification(
                    trip.getOwnerId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    title,
                    reason,
                    Map.of("contentType", ModeratedContentType.TRIP.name(),
                            "contentId", trip.getId().toString()),
                    adminId);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a trip moderation action: {}",
                    trip.getOwnerId(), exception.getMessage(), exception);
        }
    }

    private static AdminPublicTripResponse toResponse(AdminTripRow row, User owner) {
        return AdminPublicTripResponse.builder()
                .id(row.getId())
                .name(row.getName())
                .description(row.getDescription())
                .coverImageUrl(row.getCoverImageUrl())
                .destination(row.getDestination())
                .visibility(row.getVisibility())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .ownerId(row.getOwnerId())
                .ownerDisplayName(owner == null ? null : owner.getFullName())
                .ownerEmail(owner == null ? null : owner.getEmail())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .activityCount(orZero(row.getActivityCount()))
                .memoryCount(orZero(row.getMemoryCount()))
                .viewCount(orZero(row.getViewCount()))
                .copyCount(orZero(row.getCopyCount()))
                .helpfulVotes(orZero(row.getHelpfulVotes()))
                .hidden(Boolean.TRUE.equals(row.getHidden()))
                .publicSharedAt(row.getPublicSharedAt())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
