package com.ds.goroute.service.notification;

import com.ds.goroute.entity.*;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.notification.event.*;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collection;
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

    /**
     * The one way a person is named in a notification: full name, else username, else
     * "Someone". Every event goes through it so one person does not read as "linh.ng" in one
     * notification and "Linh Nguyễn" in the next.
     */
    public String actorName(UUID userId) {
        if (userId == null) return "Someone";
        return userRepository.findById(userId)
                .map(user -> user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername())
                .orElse("Someone");
    }

    public String tripName(UUID tripId) {
        return tripRepository.findById(tripId).map(Trip::getName).orElse("Trip");
    }

    private void dispatchAfterCommit(TripEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationDispatcher.dispatch(event);
                }
            });
            return;
        }

        notificationDispatcher.dispatch(event);
    }

    public void emitGeneric(UUID tripId, UUID actorId, NotificationType type,
                            Map<String, Object> data, Collection<UUID> recipientIds,
                            Collection<UUID> excludedRecipientIds) {
        try {
            Map<String, Object> metadata = new java.util.HashMap<>();
            if (data != null) {
                metadata.putAll(data);
            }
            metadata.put("tripId", tripId.toString());
            if (recipientIds != null) {
                metadata.put("recipientIds", recipientIds.stream().filter(java.util.Objects::nonNull).distinct().toList());
            }
            if (excludedRecipientIds != null) {
                metadata.put("excludedRecipientIds", excludedRecipientIds.stream().filter(java.util.Objects::nonNull).distinct().toList());
            }
            dispatchAfterCommit(GenericTripEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(type)
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.error("Failed to emit {}: {}", type, e.getMessage(), e);
        }
    }

    public void emitGenericToMembers(UUID tripId, UUID actorId, NotificationType type,
                                     Map<String, Object> data, Collection<UUID> excludedRecipientIds) {
        emitGeneric(tripId, actorId, type, data, null, excludedRecipientIds);
    }

    public void emitTripUpdated(Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit TRIP_UPDATED: {}", e.getMessage(), e);
        }
    }

    public void emitTripDeleted(Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

            TripDeletedEvent event = TripDeletedEvent.builder()
                    .tripId(trip.getId())
                    .actorId(actorId)
                    .type(NotificationType.TRIP_DELETED)
                    .tripName(trip.getName())
                    .actorName(actorName)
                    .metadata(buildMetadata(trip.getId().toString(), "/trip/" + trip.getId()))
                    .build();

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit TRIP_DELETED: {}", e.getMessage(), e);
        }
    }

    public void emitActivityCreated(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_ADDED: {}", e.getMessage(), e);
        }
    }

    public void emitActivityUpdated(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_UPDATED: {}", e.getMessage(), e);
        }
    }

    public void emitActivityDeleted(Activity activity, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(activity.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

            ActivityDeletedEvent event = ActivityDeletedEvent.builder()
                    .tripId(activity.getTripId())
                    .actorId(actorId)
                    .type(NotificationType.ACTIVITY_DELETED)
                    .activityName(activity.getName())
                    .actorName(actorName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(activity.getTripId().toString(), "/trip/" + activity.getTripId()))
                    .build();

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit ACTIVITY_DELETED: {}", e.getMessage(), e);
        }
    }

    public void emitMemberAdded(TripMember member, Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

            String newMemberName = Boolean.TRUE.equals(member.getIsGuest())
                ? member.getGuestName()
                : (member.getUserId() != null
                    ? actorName(member.getUserId())
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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_ADDED: {}", e.getMessage(), e);
        }
    }

    public void emitMemberRemoved(TripMember member, Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

            String removedMemberName = Boolean.TRUE.equals(member.getIsGuest())
                ? member.getGuestName()
                : (member.getUserId() != null
                    ? actorName(member.getUserId())
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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_REMOVED: {}", e.getMessage(), e);
        }
    }

    public void emitMemberAccepted(TripMember member, Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

            String memberName = member.getUserId() != null
                ? actorName(member.getUserId())
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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_ACCEPTED: {}", e.getMessage(), e);
        }
    }

    public void emitMemberLeft(TripMember member, Trip trip, UUID actorId) {
        try {
            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit MEMBER_LEFT: {}", e.getMessage(), e);
        }
    }

    public void emitGuestLinked(TripMember guestMember, UUID targetUserId, UUID tripId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            String linkedUserName = actorName(targetUserId);

            GuestLinkedEvent event = GuestLinkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.GUEST_LINKED)
                    .guestName(guestMember.getGuestName())
                    .linkedUserName(linkedUserName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(tripId.toString(), "/trip/" + tripId + "/members"))
                    .build();

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit GUEST_LINKED: {}", e.getMessage(), e);
        }
    }

    public void emitExpenseCreated(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_ADDED: {}", e.getMessage(), e);
        }
    }

    public void emitExpenseUpdated(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_UPDATED: {}", e.getMessage(), e);
        }
    }

    public void emitExpenseDeleted(Expense expense, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(expense.getTripId()).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit EXPENSE_DELETED: {}", e.getMessage(), e);
        }
    }

    public void emitPaymentMarked(UUID tripId, UUID expenseId, UUID splitId, ExpenseSplit split,
                                  String expenseDescription, String currency, Boolean isPaid, UUID actorId) {
        try {
            String payerName = actorName(actorId);

            UUID payeeId = split.getUserId();
            String payeeName;
            if (payeeId != null) {
                payeeName = actorName(payeeId);
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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_MARKED: {}", e.getMessage(), e);
        }
    }

    public void emitPaymentAllMarked(UUID tripId, UUID expenseId, String expenseDescription,
                                     Boolean isPaid, UUID actorId) {
        try {
            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_ALL_MARKED: {}", e.getMessage(), e);
        }
    }

    public void emitPaymentTripMarked(UUID tripId, Boolean isPaid, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            String actorName = actorName(actorId);

            PaymentTripMarkedEvent event = PaymentTripMarkedEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.PAYMENT_TRIP_MARKED)
                    .tripName(trip.getName())
                    .actorName(actorName)
                    .isPaid(isPaid)
                    .metadata(buildMetadata(tripId.toString(), "/trip/" + tripId + "/expenses"))
                    .build();

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit PAYMENT_TRIP_MARKED: {}", e.getMessage(), e);
        }
    }

    public void emitCheckin(UUID tripId, UUID activityId, UUID actorId) {
        emitCheckin(tripId, activityId, actorId, null);
    }

    /**
     * Tells the trip that somebody checked in on it.
     *
     * <p>A check-in made during a trip does not have to land on a scheduled activity -- the
     * café nobody planned is still part of the trip -- so [activityId] may be null and
     * [locationName] carries the name to show instead. The deep link follows the same
     * split: the activity when there is one, the trip otherwise.
     */
    public void emitCheckin(UUID tripId, UUID activityId, UUID actorId, String locationName) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityId == null ? null : activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName()
                    : (locationName != null && !locationName.isBlank() ? locationName : "Unknown");

            String actorName = actorName(actorId);

            CheckinEvent event = CheckinEvent.builder()
                    .tripId(tripId)
                    .actorId(actorId)
                    .type(NotificationType.CHECKIN)
                    .actorName(actorName)
                    .activityName(activityName)
                    .tripName(trip.getName())
                    .metadata(buildMetadata(
                        tripId.toString(),
                        activityId == null
                                ? "/trip/" + tripId
                                : "/trip/" + tripId + "/activities/" + activityId
                    ))
                    .build();

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit CHECKIN: {}", e.getMessage(), e);
        }
    }

    public void emitNoteCreated(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityId != null ? activityRepository.findById(activityId).orElse(null) : null;
            String activityName = activity != null ? activity.getName() : null;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit NOTE_ADDED: {}", e.getMessage(), e);
        }
    }

    public void emitNoteDeleted(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityId != null ? activityRepository.findById(activityId).orElse(null) : null;
            String activityName = activity != null ? activity.getName() : null;

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit NOTE_DELETED: {}", e.getMessage(), e);
        }
    }

    public void emitCommentCreated(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName() : "Unknown";

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit COMMENT_ADDED: {}", e.getMessage(), e);
        }
    }

    public void emitCommentDeleted(UUID tripId, UUID activityId, UUID actorId) {
        try {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) return;

            Activity activity = activityRepository.findById(activityId).orElse(null);
            String activityName = activity != null ? activity.getName() : "Unknown";

            String actorName = actorName(actorId);

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

            dispatchAfterCommit(event);
        } catch (Exception e) {
            log.error("Failed to emit COMMENT_DELETED: {}", e.getMessage(), e);
        }
    }
}
