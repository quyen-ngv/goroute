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
            case EXPENSE_ADDED -> putExpenseAdded(data, (ExpenseCreatedEvent) event);
            case EXPENSE_UPDATED -> putExpenseUpdated(data, (ExpenseUpdatedEvent) event);
            case EXPENSE_DELETED -> putExpenseDeleted(data, (ExpenseDeletedEvent) event);
            case ACTIVITY_ADDED -> putActivity(data, (ActivityCreatedEvent) event);
            case ACTIVITY_UPDATED -> putActivity(data, (ActivityUpdatedEvent) event);
            case ACTIVITY_DELETED -> putActivity(data, (ActivityDeletedEvent) event);
            case MEMBER_ADDED -> putMemberAdded(data, (MemberAddedEvent) event);
            case MEMBER_ACCEPTED -> putMemberAccepted(data, (MemberAcceptedEvent) event);
            case MEMBER_REMOVED -> putMemberRemoved(data, (MemberRemovedEvent) event);
            case MEMBER_LEFT -> putMemberLeft(data, (MemberRemovedEvent) event);
            case GUEST_LINKED -> putGuestLinked(data, (GuestLinkedEvent) event);
            case TRIP_UPDATED -> putTrip(data, (TripUpdatedEvent) event);
            case TRIP_DELETED -> putTrip(data, (TripDeletedEvent) event);
            case PAYMENT_MARKED -> putPaymentMarked(data, (PaymentMarkedEvent) event);
            case PAYMENT_ALL_MARKED -> putPaymentAllMarked(data, (PaymentAllMarkedEvent) event);
            case PAYMENT_TRIP_MARKED -> putPaymentTripMarked(data, (PaymentTripMarkedEvent) event);
            case CHECKIN -> putCheckin(data, (CheckinEvent) event);
            case NOTE_ADDED -> putNote(data, (NoteCreatedEvent) event);
            case NOTE_DELETED -> putNote(data, (NoteDeletedEvent) event);
            case COMMENT_ADDED -> putComment(data, (CommentCreatedEvent) event);
            case COMMENT_DELETED -> putComment(data, (CommentDeletedEvent) event);
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
