package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.request.TodoItemRequest;
import com.ds.goroute.dto.request.UpdateTodoItemRequest;
import com.ds.goroute.dto.request.UpdateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;
import com.ds.goroute.dto.response.TripNoteTodoItemResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripNote;
import com.ds.goroute.entity.TripNoteTodoAssignee;
import com.ds.goroute.entity.TripNoteTodoItem;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.TripNoteTodoRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.TripNoteService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TripNoteType;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TripNoteServiceImpl implements TripNoteService {

    /** Items one todo list may hold. */
    private static final int MAX_TODO_ITEMS = 200;

    private final TripNoteRepository tripNoteRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final NotificationHelper notificationHelper;
    private final TripRealtimePublisher tripRealtimePublisher;
    private final TripNoteTodoRepository todoRepository;

    public TripNoteServiceImpl(TripNoteRepository tripNoteRepository,
                               TripRepository tripRepository,
                               TripMemberRepository tripMemberRepository,
                               UserRepository userRepository,
                               ActivityRepository activityRepository,
                               NotificationHelper notificationHelper,
                               TripRealtimePublisher tripRealtimePublisher,
                               TripNoteTodoRepository todoRepository) {
        this.tripNoteRepository = tripNoteRepository;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.notificationHelper = notificationHelper;
        this.tripRealtimePublisher = tripRealtimePublisher;
        this.todoRepository = todoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripNoteResponse> getTripNotes(UUID tripId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        List<TripNote> notes = tripNoteRepository.findByTripId(tripId);

        return toResponses(notes.stream()
                .filter(note -> isVisibleTo(note, userId))
                .collect(Collectors.toList()));
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

        return toResponses(notes.stream()
                .filter(note -> isVisibleTo(note, userId))
                .collect(Collectors.toList()));
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
        TripNoteType noteType = parseNoteType(request.getNoteType());

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
                .noteType(noteType)
                // A todo list is for the group: its items are assigned to other people.
                .isShared(noteType == TripNoteType.TODO || request.getIsShared() == null || request.getIsShared())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        tripNoteRepository.insert(note);
        log.info("Trip note created: {} for trip: {}", note.getId(), tripId);

        Set<UUID> assigned = new LinkedHashSet<>();
        if (noteType == TripNoteType.TODO && request.getTodoItems() != null) {
            Set<UUID> allowed = assignableUserIds(tripId);
            int sortOrder = 0;
            for (TodoItemRequest itemRequest : request.getTodoItems()) {
                List<UUID> assignees = checkAssignees(itemRequest.getAssigneeIds(), allowed);
                TripNoteTodoItem item = insertTodoItem(note, itemRequest.getText(), sortOrder++, userId);
                todoRepository.replaceAssignees(item.getId(), assignees);
                notifyAssigned(tripId, userId, item, assignees);
                assigned.addAll(assignees);
            }
        }

        if (Boolean.TRUE.equals(note.getIsShared())) {
            // People assigned an item already get TODO_ASSIGNED; one push is enough.
            notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.NOTE_ADDED,
                    noteNotificationData(tripId, activityId, userId), assigned);
            publishSharedNoteChange(TripRealtimeEventType.NOTE_CREATED, tripId, note, userId);
        }

        return toTripNoteResponse(note);
    }

    @Override
    @Transactional
    public void deleteTripNote(UUID tripId, UUID noteId, UUID userId) {
        // Verify user is member of trip
        Trip trip = verifyTripMember(tripId, userId);

        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));

        // Only note owner can delete; a todo list belongs to the group, so the trip owner may too.
        boolean isAuthor = userId.equals(note.getUserId());
        boolean ownerMayDelete = isTodo(note) && tripId.equals(note.getTripId())
                && userId.equals(trip.getOwnerId());
        if (!isAuthor && !ownerMayDelete) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only delete your own notes");
        }

        tripNoteRepository.softDelete(noteId);
        if (isTodo(note)) {
            todoRepository.softDeleteItemsOfNote(noteId);
        }
        log.info("Trip note deleted: {}", noteId);

        if (Boolean.TRUE.equals(note.getIsShared())) {
            notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.NOTE_DELETED,
                    noteNotificationData(tripId, note.getActivityId(), userId), null);
            publishSharedNoteChange(TripRealtimeEventType.NOTE_DELETED, tripId, note, userId);
        }
    }

    private Trip verifyTripMember(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a member of this trip"));

        if (member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a member of this trip");
        }
        return trip;
    }

    private void verifyActivityBelongsToTrip(UUID activityId, UUID tripId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));

        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Activity does not belong to this trip");
        }
    }

    private TripNoteResponse toTripNoteResponse(TripNote note) {
        return toResponses(List.of(note)).get(0);
    }

    /** One query each for items, assignees and people, whatever the number of notes. */
    private List<TripNoteResponse> toResponses(List<TripNote> notes) {
        if (notes.isEmpty()) return List.of();
        List<UUID> todoNoteIds = notes.stream().filter(this::isTodo).map(TripNote::getId).toList();
        List<TripNoteTodoItem> items = todoRepository.findItemsByNoteIds(todoNoteIds);
        List<TripNoteTodoAssignee> assignees = todoRepository.findAssignees(
                items.stream().map(TripNoteTodoItem::getId).toList());

        Set<UUID> userIds = new LinkedHashSet<>();
        notes.forEach(note -> userIds.add(note.getUserId()));
        items.forEach(item -> userIds.add(item.getDoneBy()));
        assignees.forEach(assignee -> userIds.add(assignee.getUserId()));
        userIds.remove(null);
        Map<UUID, UserResponse> people = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findByIds(userIds)) {
                people.put(user.getId(), person(user));
            }
        }

        Map<UUID, List<UserResponse>> assigneesByItem = new HashMap<>();
        for (TripNoteTodoAssignee assignee : assignees) {
            UserResponse user = people.get(assignee.getUserId());
            if (user != null) {
                assigneesByItem.computeIfAbsent(assignee.getItemId(), id -> new ArrayList<>()).add(user);
            }
        }
        Map<UUID, List<TripNoteTodoItemResponse>> itemsByNote = new HashMap<>();
        for (TripNoteTodoItem item : items) {
            itemsByNote.computeIfAbsent(item.getNoteId(), id -> new ArrayList<>()).add(TripNoteTodoItemResponse.builder()
                    .id(item.getId())
                    .text(item.getText())
                    .sortOrder(item.getSortOrder())
                    .isDone(Boolean.TRUE.equals(item.getIsDone()))
                    .doneBy(item.getDoneBy() == null ? null : people.get(item.getDoneBy()))
                    .doneAt(item.getDoneAt())
                    .assignees(assigneesByItem.getOrDefault(item.getId(), List.of()))
                    .createdAt(item.getCreatedAt())
                    .build());
        }

        List<TripNoteResponse> responses = new ArrayList<>(notes.size());
        for (TripNote note : notes) {
            // A note whose author is gone keeps an empty name instead of failing the whole list.
            UserResponse author = people.getOrDefault(note.getUserId(),
                    UserResponse.builder().id(note.getUserId()).fullName("").build());
            responses.add(TripNoteResponse.builder()
                    .id(note.getId())
                    .tripId(note.getTripId())
                    .activityId(note.getActivityId())
                    .user(author)
                    .noteType(typeOf(note).name())
                    .content(note.getContent())
                    .todoItems(itemsByNote.getOrDefault(note.getId(), List.of()))
                    .isShared(note.getIsShared() == null || note.getIsShared())
                    .createdAt(note.getCreatedAt())
                    .build());
        }
        return responses;
    }

    private static UserResponse person(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    @Override
    @Transactional
    public TripNoteResponse updateTripNote(UUID tripId, UUID noteId, UpdateTripNoteRequest request, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);

        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));

        // Only note owner can update; a todo list's title is the group's, like its items.
        if (isTodo(note)) {
            requireNoteOfTrip(note, tripId);
        } else if (!userId.equals(note.getUserId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only update your own notes");
        }

        boolean wasShared = Boolean.TRUE.equals(note.getIsShared());
        note.setContent(request.getContent());
        // A todo list stays shared.
        if (request.getIsShared() != null && !isTodo(note)) {
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

    // Item changes publish note.updated so open screens refresh, but send no notification:
    // a tick on every item would push the whole group each time. Only a new assignee is told.

    @Override
    @Transactional
    public TripNoteResponse addTodoItem(UUID tripId, UUID noteId, TodoItemRequest request, UUID userId) {
        verifyTripMember(tripId, userId);
        TripNote note = requireTodoNote(tripId, noteId);
        if (todoRepository.countItems(noteId) >= MAX_TODO_ITEMS) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "A todo list holds at most " + MAX_TODO_ITEMS + " items");
        }
        List<UUID> assignees = checkAssignees(request.getAssigneeIds(), assignableUserIds(tripId));
        TripNoteTodoItem item = insertTodoItem(note, request.getText(), todoRepository.nextSortOrder(noteId), userId);
        todoRepository.replaceAssignees(item.getId(), assignees);
        notifyAssigned(tripId, userId, item, assignees);
        publishSharedNoteChange(TripRealtimeEventType.NOTE_UPDATED, tripId, note, userId);
        return toTripNoteResponse(note);
    }

    @Override
    @Transactional
    public TripNoteResponse updateTodoItem(UUID tripId, UUID noteId, UUID itemId,
                                           UpdateTodoItemRequest request, UUID userId) {
        verifyTripMember(tripId, userId);
        TripNote note = requireTodoNote(tripId, noteId);
        TripNoteTodoItem item = requireItemOfNote(itemId, noteId);

        if (request.getText() != null) {
            String text = request.getText().trim();
            if (text.isEmpty()) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "Text is required");
            }
            item.setText(text);
        }
        if (request.getIsDone() != null && request.getIsDone() != Boolean.TRUE.equals(item.getIsDone())) {
            // Done once for everyone: whoever ticks it is recorded, unticking clears that.
            item.setIsDone(request.getIsDone());
            item.setDoneBy(request.getIsDone() ? userId : null);
            item.setDoneAt(request.getIsDone() ? LocalDateTime.now() : null);
        }
        todoRepository.updateItem(item);

        if (request.getAssigneeIds() != null) {
            List<UUID> assignees = checkAssignees(request.getAssigneeIds(), assignableUserIds(tripId));
            Set<UUID> before = todoRepository.findAssignees(List.of(itemId)).stream()
                    .map(TripNoteTodoAssignee::getUserId)
                    .collect(Collectors.toSet());
            todoRepository.replaceAssignees(itemId, assignees);
            notifyAssigned(tripId, userId, item, assignees.stream().filter(id -> !before.contains(id)).toList());
        }

        publishSharedNoteChange(TripRealtimeEventType.NOTE_UPDATED, tripId, note, userId);
        return toTripNoteResponse(note);
    }

    @Override
    @Transactional
    public TripNoteResponse deleteTodoItem(UUID tripId, UUID noteId, UUID itemId, UUID userId) {
        verifyTripMember(tripId, userId);
        TripNote note = requireTodoNote(tripId, noteId);
        requireItemOfNote(itemId, noteId);
        todoRepository.softDeleteItem(itemId);
        publishSharedNoteChange(TripRealtimeEventType.NOTE_UPDATED, tripId, note, userId);
        return toTripNoteResponse(note);
    }

    private TripNoteTodoItem insertTodoItem(TripNote note, String text, int sortOrder, UUID userId) {
        TripNoteTodoItem item = TripNoteTodoItem.builder()
                .id(UUID.randomUUID())
                .noteId(note.getId())
                .tripId(note.getTripId())
                .text(text.trim())
                .sortOrder(sortOrder)
                .isDone(false)
                .createdBy(userId)
                .build();
        todoRepository.insertItem(item);
        return item;
    }

    /** A todo-list note of this trip. Todo lists are always shared, so every member may see it. */
    private TripNote requireTodoNote(UUID tripId, UUID noteId) {
        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));
        requireNoteOfTrip(note, tripId);
        if (!isTodo(note)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "This note is not a todo list");
        }
        return note;
    }

    /** Any member may edit someone else's todo list, so the trip in the path must be the note's. */
    private static void requireNoteOfTrip(TripNote note, UUID tripId) {
        if (!tripId.equals(note.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Note not found");
        }
    }

    private TripNoteTodoItem requireItemOfNote(UUID itemId, UUID noteId) {
        TripNoteTodoItem item = todoRepository.findItemById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Todo item not found"));
        if (!noteId.equals(item.getNoteId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Todo item not found");
        }
        return item;
    }

    /** People an item may be assigned to: the trip owner and members who have joined. */
    private Set<UUID> assignableUserIds(UUID tripId) {
        Set<UUID> ids = new LinkedHashSet<>();
        tripRepository.findById(tripId).map(Trip::getOwnerId).ifPresent(ids::add);
        tripMemberRepository.findByTripId(tripId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACCEPTED)
                .map(TripMember::getUserId)
                .forEach(ids::add);
        ids.remove(null);
        return ids;
    }

    private static List<UUID> checkAssignees(Collection<UUID> requested, Set<UUID> allowed) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<UUID> assignees = requested.stream().filter(Objects::nonNull).distinct().toList();
        for (UUID id : assignees) {
            if (!allowed.contains(id)) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "Assignee is not a member of this trip");
            }
        }
        return assignees;
    }

    private void notifyAssigned(UUID tripId, UUID actorId, TripNoteTodoItem item, List<UUID> assignees) {
        List<UUID> recipients = assignees.stream().filter(id -> !id.equals(actorId)).toList();
        if (recipients.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("actorName", notificationHelper.actorName(actorId));
        data.put("tripName", notificationHelper.tripName(tripId));
        data.put("todoText", item.getText());
        data.put("noteId", item.getNoteId().toString());
        data.put("todoItemId", item.getId().toString());
        data.put("deepLink", "/trip/" + tripId + "/notes");
        notificationHelper.emitGeneric(tripId, actorId, NotificationType.TODO_ASSIGNED, data, recipients, null);
    }

    private boolean isTodo(TripNote note) {
        return typeOf(note) == TripNoteType.TODO;
    }

    private static TripNoteType typeOf(TripNote note) {
        return note.getNoteType() == null ? TripNoteType.TEXT : note.getNoteType();
    }

    private static TripNoteType parseNoteType(String value) {
        if (value == null || value.isBlank()) return TripNoteType.TEXT;
        try {
            return TripNoteType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unknown note type: " + value);
        }
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
