package com.ds.goroute.service.notification;

import com.ds.goroute.entity.*;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.notification.event.*;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationHelper {

    private final NotificationDispatcher notificationDispatcher;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final ActivityRepository activityRepository;

    private Map<String, Object> buildMetadata(String tripId, String deepLink) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("tripId", tripId);
        if (deepLink != null) {
            metadata.put("deepLink", deepLink);
        }
        return metadata;
    }

    @Async
    public void emitTripUpdated(Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            TripUpdatedEvent event = TripUpdatedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.TRIP_UPDATED)
                    .tripName(trip.getName())
                    .actorName(actorName)
                    .metadata(Map.of(
                        "tripId", trip.getId().toString(),
                        "deepLink", "/trip/" + trip.getId()
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit TRIP_UPDATED: {}", e.getMessage());
        }
    }

    @Async
    public void emitTripDeleted(Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            TripDeletedEvent event = TripDeletedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.TRIP_DELETED)
                    .tripName(trip.getName())
                    .actorName(actorName)
                    .metadata(buildMetadata(trip.getId().toString(), "/trip/" + trip.getId()))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit TRIP_DELETED: {}", e.getMessage());
        }
    }

    @Async
    public void emitActivityCreated(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            ActivityCreatedEvent event = ActivityCreatedEvent.builder()
                    .tripId(activity.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.ACTIVITY_ADDED)
                    .activityName(activity.getName())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        activity.getTripId().toString(),
                        "/trip/" + activity.getTripId() + "/activities/" + activity.getId()
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_ADDED: {}", e.getMessage());
        }
    }

    @Async
    public void emitActivityUpdated(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            ActivityUpdatedEvent event = ActivityUpdatedEvent.builder()
                    .tripId(activity.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.ACTIVITY_UPDATED)
                    .activityName(activity.getName())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        activity.getTripId().toString(),
                        "/trip/" + activity.getTripId() + "/activities/" + activity.getId()
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_UPDATED: {}", e.getMessage());
        }
    }

    @Async
    public void emitActivityDeleted(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            ActivityDeletedEvent event = ActivityDeletedEvent.builder()
                    .tripId(activity.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.ACTIVITY_DELETED)
                    .activityName(activity.getName())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(activity.getTripId().toString(), "/trip/" + activity.getTripId()))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_DELETED: {}", e.getMessage());
        }
    }

    @Async
    public void emitMemberAdded(TripMember member, Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            String newMemberName = Boolean.TRUE.equals(member.getIsGuest())
                ? member.getGuestName()
                : (member.getUserId() != null
                    ? userRepository.findById(member.getUserId()).map(User::getUsername).orElse("Someone")
                    : "Someone");

            MemberAddedEvent event = MemberAddedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.MEMBER_ADDED)
                    .newMemberName(newMemberName)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(trip.getId().toString(), "/trip/" + trip.getId() + "/members"))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_ADDED: {}", e.getMessage());
        }
    }

    @Async
    public void emitMemberRemoved(TripMember member, Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            String removedMemberName = Boolean.TRUE.equals(member.getIsGuest())
                ? member.getGuestName()
                : (member.getUserId() != null
                    ? userRepository.findById(member.getUserId()).map(User::getUsername).orElse("Someone")
                    : "Someone");

            Map<String, Object> metadata = buildMetadata(
                trip.getId().toString(),
                "/trip/" + trip.getId() + "/members"
            );
            if (member.getUserId() != null) {
                metadata.put("removedMemberId", member.getUserId());
            }

            MemberRemovedEvent event = MemberRemovedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.MEMBER_REMOVED)
                    .removedMemberId(member.getUserId())
                    .removedMemberName(removedMemberName)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_REMOVED: {}", e.getMessage());
        }
    }

    @Async
    public void emitMemberAccepted(TripMember member, Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            String memberName = member.getUserId() != null
                ? userRepository.findById(member.getUserId()).map(User::getUsername).orElse("Someone")
                : "Someone";

            MemberAcceptedEvent event = MemberAcceptedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.MEMBER_ACCEPTED)
                    .memberName(memberName)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        trip.getId().toString(),
                        "/trip/" + trip.getId() + "/members"
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_ACCEPTED: {}", e.getMessage());
        }
    }

    @Async
    public void emitMemberLeft(TripMember member, Trip trip, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            MemberRemovedEvent event = MemberRemovedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.MEMBER_LEFT)
                    .memberName(actorName) // User left themselves
                    .removedMemberName(actorName)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        trip.getId().toString(),
                        "/trip/" + trip.getId() + "/members"
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_LEFT: {}", e.getMessage());
        }
    }

    @Async
    public void emitGuestLinked(TripMember guestMember, UUID targetUserId, UUID tripId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            User linkedUser = userRepository.findById(targetUserId).orElse(null);
            String linkedUserName = linkedUser != null ? linkedUser.getUsername() : "Someone";

            GuestLinkedEvent event = GuestLinkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.GUEST_LINKED)
                    .guestName(guestMember.getGuestName())
                    .linkedUserName(linkedUserName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(tripId.toString(), "/trip/" + tripId + "/members"))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit GUEST_LINKED: {}", e.getMessage());
        }
    }

    @Async
    public void emitExpenseCreated(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            Map<String, Object> metadata = buildMetadata(
                expense.getTripId().toString(),
                "/trip/" + expense.getTripId() + "/expenses/" + expense.getId()
            );
            metadata.put("expenseId", expense.getId());

            ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                    .tripId(expense.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.EXPENSE_ADDED)
                    .expenseId(expense.getId())
                    .description(expense.getDescription())
                    .amount(expense.getAmount())
                    .currency(expense.getCurrency())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_ADDED: {}", e.getMessage());
        }
    }

    @Async
    public void emitExpenseUpdated(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            Map<String, Object> metadata = buildMetadata(
                expense.getTripId().toString(),
                "/trip/" + expense.getTripId() + "/expenses/" + expense.getId()
            );
            metadata.put("expenseId", expense.getId());

            ExpenseUpdatedEvent event = ExpenseUpdatedEvent.builder()
                    .tripId(expense.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.EXPENSE_UPDATED)
                    .expenseId(expense.getId())
                    .description(expense.getDescription())
                    .amount(expense.getAmount())
                    .currency(expense.getCurrency())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_UPDATED: {}", e.getMessage());
        }
    }

    @Async
    public void emitExpenseDeleted(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            Map<String, Object> metadata = buildMetadata(expense.getTripId().toString(), "/trip/" + expense.getTripId() + "/expenses");
            metadata.put("expenseId", expense.getId());

            ExpenseDeletedEvent event = ExpenseDeletedEvent.builder()
                    .tripId(expense.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.EXPENSE_DELETED)
                    .expenseId(expense.getId())
                    .description(expense.getDescription())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_DELETED: {}", e.getMessage());
        }
    }

    @Async
    public void emitPaymentMarked(UUID tripId, UUID expenseId, UUID splitId, ExpenseSplit split,
                                  String expenseDescription, String currency, Boolean isPaid, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String payerName = actor != null ? actor.getUsername() : "Someone";

            UUID payeeId = split.getUserId();
            String payeeName;
            if (payeeId != null) {
                User payee = userRepository.findById(payeeId).orElse(null);
                payeeName = payee != null ? payee.getUsername() : "Someone";
            } else {
                payeeName = split.getGuestName() != null ? split.getGuestName() : "Someone";
            }

            Map<String, Object> metadata = buildMetadata(
                tripId.toString(),
                "/trip/" + tripId + "/expenses/" + expenseId
            );
            if (payeeId != null) {
                metadata.put("payeeId", payeeId);
            }

            PaymentMarkedEvent event = PaymentMarkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.PAYMENT_MARKED)
                    .payeeId(payeeId)
                    .payeeName(payeeName)
                    .payerName(payerName)
                    .amount(split.getAmount())
                    .currency(currency)
                    .expenseDescription(expenseDescription)
                    .isPaid(isPaid)
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_MARKED: {}", e.getMessage());
        }
    }

    @Async
    public void emitPaymentAllMarked(UUID tripId, UUID expenseId, String expenseDescription,
                                     Boolean isPaid, UUID actorId) {
        try {
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            Map<String, Object> metadata = buildMetadata(
                tripId.toString(),
                "/trip/" + tripId + "/expenses/" + expenseId
            );
            metadata.put("expenseId", expenseId);

            PaymentAllMarkedEvent event = PaymentAllMarkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.PAYMENT_ALL_MARKED)
                    .expenseId(expenseId)
                    .expenseDescription(expenseDescription)
                    .actorName(actorName)
                    .isPaid(isPaid)
                    .metadata(metadata)
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_ALL_MARKED: {}", e.getMessage());
        }
    }

    @Async
    public void emitPaymentTripMarked(UUID tripId, Boolean isPaid, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            PaymentTripMarkedEvent event = PaymentTripMarkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.PAYMENT_TRIP_MARKED)
                    .tripName(trip.getName())
                    .actorName(actorName)
                    .isPaid(isPaid)
                    .metadata(buildMetadata(tripId.toString(), "/trip/" + tripId + "/expenses"))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_TRIP_MARKED: {}", e.getMessage());
        }
    }

    @Async
    public void emitCheckin(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName() : "Unknown";

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            CheckinEvent event = CheckinEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.CHECKIN)
                    .actorName(actorName)
                    .activityName(activityName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        tripId.toString(),
                        "/trip/" + tripId + "/activities/" + activityId
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit CHECKIN: {}", e.getMessage());
        }
    }

    @Async
    public void emitNoteCreated(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityId != null ? activityRepository.findById(activityId).orElse(null) : null;
            String activityName = activity != null ? activity.getName() : null;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            String deepLink = activityId != null
                ? "/trip/" + tripId + "/activities/" + activityId + "/notes"
                : "/trip/" + tripId + "/notes";

            NoteCreatedEvent event = NoteCreatedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.NOTE_ADDED)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .activityName(activityName)
                    .metadata(buildMetadata(tripId.toString(), deepLink))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit NOTE_ADDED: {}", e.getMessage());
        }
    }

    @Async
    public void emitNoteDeleted(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityId != null ? activityRepository.findById(activityId).orElse(null) : null;
            String activityName = activity != null ? activity.getName() : null;

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            String deepLink = activityId != null
                ? "/trip/" + tripId + "/activities/" + activityId + "/notes"
                : "/trip/" + tripId + "/notes";

            NoteDeletedEvent event = NoteDeletedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.NOTE_DELETED)
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .activityName(activityName)
                    .metadata(buildMetadata(tripId.toString(), deepLink))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit NOTE_DELETED: {}", e.getMessage());
        }
    }

    @Async
    public void emitCommentCreated(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName() : "Unknown";

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            CommentCreatedEvent event = CommentCreatedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.COMMENT_ADDED)
                    .actorName(actorName)
                    .activityName(activityName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        tripId.toString(),
                        "/trip/" + tripId + "/activities/" + activityId + "/comments"
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit COMMENT_ADDED: {}", e.getMessage());
        }
    }

    @Async
    public void emitCommentDeleted(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName() : "Unknown";

            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getUsername() : "Someone";

            CommentDeletedEvent event = CommentDeletedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.COMMENT_DELETED)
                    .actorName(actorName)
                    .activityName(activityName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        tripId.toString(),
                        "/trip/" + tripId + "/activities/" + activityId + "/comments"
                    ))
                    .build();

            notificationDispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to emit COMMENT_DELETED: {}", e.getMessage());
        }
    }
}

