package com.ds.goroute.service.notification;

import com.ds.goroute.service.notification.event.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationPayloadFactory {

    public Map<String, Object> build(TripEvent event) {
        Map<String, Object> data = new HashMap<>();
        if (event.getMetadata() != null) {
            data.putAll(event.getMetadata());
        }

        data.put("type", event.getType().name());
        data.put("tripId", event.getTripId().toString());

        switch (event.getType()) {
            case EXPENSE_ADDED -> {
                if (event instanceof ExpenseCreatedEvent typed) putExpenseAdded(data, typed);
            }
            case EXPENSE_UPDATED -> {
                if (event instanceof ExpenseUpdatedEvent typed) putExpenseUpdated(data, typed);
            }
            case EXPENSE_DELETED -> {
                if (event instanceof ExpenseDeletedEvent typed) putExpenseDeleted(data, typed);
            }
            case ACTIVITY_ADDED -> {
                if (event instanceof ActivityCreatedEvent typed) putActivity(data, typed);
            }
            case ACTIVITY_UPDATED -> {
                if (event instanceof ActivityUpdatedEvent typed) putActivity(data, typed);
            }
            case ACTIVITY_DELETED -> {
                if (event instanceof ActivityDeletedEvent typed) putActivity(data, typed);
            }
            case MEMBER_ADDED -> {
                if (event instanceof MemberAddedEvent typed) putMemberAdded(data, typed);
            }
            case MEMBER_ACCEPTED -> {
                if (event instanceof MemberAcceptedEvent typed) putMemberAccepted(data, typed);
            }
            case MEMBER_REMOVED, MEMBER_LEFT -> {
                if (event instanceof MemberRemovedEvent typed) putMemberRemoved(data, typed);
            }
            case GUEST_LINKED -> {
                if (event instanceof GuestLinkedEvent typed) putGuestLinked(data, typed);
            }
            case TRIP_UPDATED -> {
                if (event instanceof TripUpdatedEvent typed) putTrip(data, typed);
            }
            case TRIP_DELETED -> {
                if (event instanceof TripDeletedEvent typed) putTrip(data, typed);
            }
            case PAYMENT_MARKED -> {
                if (event instanceof PaymentMarkedEvent typed) putPaymentMarked(data, typed);
            }
            case PAYMENT_ALL_MARKED -> {
                if (event instanceof PaymentAllMarkedEvent typed) putPaymentAllMarked(data, typed);
            }
            case PAYMENT_TRIP_MARKED -> {
                if (event instanceof PaymentTripMarkedEvent typed) putPaymentTripMarked(data, typed);
            }
            case CHECKIN -> {
                if (event instanceof CheckinEvent typed) putCheckin(data, typed);
            }
            case NOTE_ADDED -> {
                if (event instanceof NoteCreatedEvent typed) putNote(data, typed);
            }
            case NOTE_DELETED -> {
                if (event instanceof NoteDeletedEvent typed) putNote(data, typed);
            }
            case COMMENT_ADDED -> {
                if (event instanceof CommentCreatedEvent typed) putComment(data, typed);
            }
            case COMMENT_DELETED -> {
                if (event instanceof CommentDeletedEvent typed) putComment(data, typed);
            }
            default -> {
            }
        }

        return data;
    }

    private void putExpenseAdded(Map<String, Object> data, ExpenseCreatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("expenseName", event.getDescription());
        data.put("amount", event.getAmount());
        data.put("currency", event.getCurrency());
        data.put("tripName", event.getTripName());
    }

    private void putExpenseUpdated(Map<String, Object> data, ExpenseUpdatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("expenseName", event.getDescription());
        data.put("tripName", event.getTripName());
    }

    private void putExpenseDeleted(Map<String, Object> data, ExpenseDeletedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("expenseName", event.getDescription());
        data.put("tripName", event.getTripName());
    }

    private void putActivity(Map<String, Object> data, ActivityCreatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }

    private void putActivity(Map<String, Object> data, ActivityUpdatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }

    private void putActivity(Map<String, Object> data, ActivityDeletedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }

    private void putMemberAdded(Map<String, Object> data, MemberAddedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("newMemberName", event.getNewMemberName());
        data.put("tripName", event.getTripName());
    }

    private void putMemberAccepted(Map<String, Object> data, MemberAcceptedEvent event) {
        data.put("memberName", event.getMemberName());
        data.put("tripName", event.getTripName());
    }

    private void putMemberRemoved(Map<String, Object> data, MemberRemovedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("removedMemberName", event.getRemovedMemberName());
        data.put("tripName", event.getTripName());
    }

    private void putMemberLeft(Map<String, Object> data, MemberRemovedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
    }

    private void putGuestLinked(Map<String, Object> data, GuestLinkedEvent event) {
        data.put("guestName", event.getGuestName());
        data.put("linkedUserName", event.getLinkedUserName());
        data.put("tripName", event.getTripName());
    }

    private void putTrip(Map<String, Object> data, TripUpdatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
    }

    private void putTrip(Map<String, Object> data, TripDeletedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
    }

    private void putPaymentMarked(Map<String, Object> data, PaymentMarkedEvent event) {
        data.put("payerName", event.getPayerName());
        data.put("payeeName", event.getPayeeName());
        data.put("amount", event.getAmount());
        data.put("currency", event.getCurrency());
        data.put("expenseDescription", event.getExpenseDescription());
    }

    private void putPaymentAllMarked(Map<String, Object> data, PaymentAllMarkedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("expenseDescription", event.getExpenseDescription());
        data.put("isPaid", event.getIsPaid());
    }

    private void putPaymentTripMarked(Map<String, Object> data, PaymentTripMarkedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
        data.put("isPaid", event.getIsPaid());
    }

    private void putCheckin(Map<String, Object> data, CheckinEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }

    private void putNote(Map<String, Object> data, NoteCreatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
        data.put("activityName", event.getActivityName());
    }

    private void putNote(Map<String, Object> data, NoteDeletedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("tripName", event.getTripName());
        data.put("activityName", event.getActivityName());
    }

    private void putComment(Map<String, Object> data, CommentCreatedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }

    private void putComment(Map<String, Object> data, CommentDeletedEvent event) {
        data.put("actorName", event.getActorName());
        data.put("activityName", event.getActivityName());
        data.put("tripName", event.getTripName());
    }
}
