package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.request.UpdateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripNote;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.TripNoteService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TripNoteServiceImpl implements TripNoteService {

    private final TripNoteRepository tripNoteRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final NotificationHelper notificationHelper;
    private final TripRealtimePublisher tripRealtimePublisher;

    public TripNoteServiceImpl(TripNoteRepository tripNoteRepository,
                               TripRepository tripRepository,
                               TripMemberRepository tripMemberRepository,
                               UserRepository userRepository,
                               ActivityRepository activityRepository,
                               NotificationHelper notificationHelper,
                               TripRealtimePublisher tripRealtimePublisher) {
        this.tripNoteRepository = tripNoteRepository;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.notificationHelper = notificationHelper;
        this.tripRealtimePublisher = tripRealtimePublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripNoteResponse> getTripNotes(UUID tripId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        List<TripNote> notes = tripNoteRepository.findByTripId(tripId);

        return notes.stream()
                .filter(note -> isVisibleTo(note, userId))
                .map(this::toTripNoteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripNoteResponse> getActivityNotes(UUID tripId, UUID activityId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        // Membership of this trip says nothing about an activity id from somewhere else;
        // without this, any member of any trip could read any activity's notes.
        verifyActivityBelongsToTrip(activityId, tripId);

        List<TripNote> notes = tripNoteRepository.findByActivityId(activityId);

        return notes.stream()
                .filter(note -> isVisibleTo(note, userId))
                .map(this::toTripNoteResponse)
                .collect(Collectors.toList());
    }

    /**
     * A note is for the whole trip unless its author marked it private. Rows written
     * before {@code is_shared} existed have it null, and every other read path in this
     * class already reads null as shared, so null stays visible to everyone.
     */
    private boolean isVisibleTo(TripNote note, UUID userId) {
        if (!Boolean.FALSE.equals(note.getIsShared())) {
            return true;
        }
        // Notes written before V019 have a null user_id: that migration added the column and
        // dropped created_by without backfilling. There is no author to compare against, so
        // an ownerless note stays visible rather than throwing.
        return note.getUserId() != null && note.getUserId().equals(userId);
    }

    @Override
    @Transactional
    public TripNoteResponse createTripNote(UUID tripId, CreateTripNoteRequest request, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        UUID activityId = null;
        // If activityId is provided, verify it belongs to the trip
        if (request.getActivityId() != null && !request.getActivityId().isEmpty()) {
            activityId = UUID.fromString(request.getActivityId());
            verifyActivityBelongsToTrip(activityId, tripId);
        }

        TripNote note = TripNote.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(activityId)
                .userId(userId)
                .content(request.getContent())
                .isShared(request.getIsShared() == null || request.getIsShared())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        tripNoteRepository.insert(note);
        log.info("Trip note created: {} for trip: {}", note.getId(), tripId);

        if (Boolean.TRUE.equals(note.getIsShared())) {
            notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.NOTE_ADDED,
                    noteNotificationData(tripId, activityId, userId), null);
            publishSharedNoteChange(TripRealtimeEventType.NOTE_CREATED, tripId, note, userId);
        }

        return toTripNoteResponse(note);
    }

    @Override
    @Transactional
    public void deleteTripNote(UUID tripId, UUID noteId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));

        // Only note owner can delete
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only delete your own notes");
        }

        tripNoteRepository.softDelete(noteId);
        log.info("Trip note deleted: {}", noteId);

        if (Boolean.TRUE.equals(note.getIsShared())) {
            notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.NOTE_DELETED,
                    noteNotificationData(tripId, note.getActivityId(), userId), null);
            publishSharedNoteChange(TripRealtimeEventType.NOTE_DELETED, tripId, note, userId);
        }
    }

    private void verifyTripMember(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a member of this trip"));

        if (member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a member of this trip");
        }
    }

    private void verifyActivityBelongsToTrip(UUID activityId, UUID tripId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));

        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Activity does not belong to this trip");
        }
    }

    private TripNoteResponse toTripNoteResponse(TripNote note) {
        User user = userRepository.findById(note.getUserId()).orElse(null);

        return TripNoteResponse.builder()
                .id(note.getId())
                .tripId(note.getTripId())
                .activityId(note.getActivityId())
                .user(UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .content(note.getContent())
                .isShared(note.getIsShared() == null || note.getIsShared())
                .createdAt(note.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public TripNoteResponse updateTripNote(UUID tripId, UUID noteId, UpdateTripNoteRequest request, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));

        // Only note owner can update
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only update your own notes");
        }

        boolean wasShared = Boolean.TRUE.equals(note.getIsShared());
        note.setContent(request.getContent());
        if (request.getIsShared() != null) {
            note.setIsShared(request.getIsShared());
        }
        note.setUpdatedAt(LocalDateTime.now());

        tripNoteRepository.updateById(note);
        log.info("Trip note updated: {}", noteId);

        boolean isShared = Boolean.TRUE.equals(note.getIsShared());
        if (wasShared || isShared) {
            NotificationType type = !wasShared ? NotificationType.NOTE_ADDED
                    : !isShared ? NotificationType.NOTE_DELETED
                    : NotificationType.NOTE_UPDATED;
            notificationHelper.emitGenericToMembers(tripId, userId, type,
                    noteNotificationData(tripId, note.getActivityId(), userId), null);
            publishSharedNoteChange(TripRealtimeEventType.NOTE_UPDATED, tripId, note, userId);
        }

        return toTripNoteResponse(note);
    }

    private Map<String, Object> noteNotificationData(UUID tripId, UUID activityId, UUID actorId) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorName", notificationHelper.actorName(actorId));
        data.put("tripName", notificationHelper.tripName(tripId));
        if (activityId != null) {
            activityRepository.findById(activityId).ifPresent(activity -> {
                data.put("activityName", activity.getName());
                data.put("activityId", activityId);
            });
            data.put("deepLink", "/trip/" + tripId + "/activities/" + activityId + "/notes");
        } else {
            data.put("activityName", notificationHelper.tripName(tripId));
            data.put("deepLink", "/trip/" + tripId + "/notes");
        }
        return data;
    }

    private void publishSharedNoteChange(
            TripRealtimeEventType type,
            UUID tripId,
            TripNote note,
            UUID actorId) {
        Map<String, Object> payload = note.getActivityId() == null
                ? Map.of()
                : Map.of("activityId", note.getActivityId());
        tripRealtimePublisher.publishAfterCommit(type, tripId, note.getId(), actorId, payload);
    }
}
