package com.ds.goroute.type;

/**
 * Stable, version-one names for the small invalidation events emitted on a trip topic.
 *
 * <p>These values are part of the WebSocket contract. Clients must ignore an event name they do
 * not understand so a newer server never breaks an older app.
 */
public enum TripRealtimeEventType {
    TRIP_UPDATED("trip.updated"),
    TRIP_DELETED("trip.deleted"),
    ACTIVITY_CREATED("activity.created"),
    ACTIVITY_UPDATED("activity.updated"),
    ACTIVITY_DELETED("activity.deleted"),
    ACTIVITY_REORDERED("activity.reordered"),
    EXPENSE_CREATED("expense.created"),
    EXPENSE_UPDATED("expense.updated"),
    EXPENSE_DELETED("expense.deleted"),
    EXPENSE_SPLITS_UPDATED("expense.splitsUpdated"),
    EXPENSE_PAYMENT_UPDATED("expense.paymentUpdated"),
    NOTE_CREATED("note.created"),
    NOTE_UPDATED("note.updated"),
    NOTE_DELETED("note.deleted"),
    MEMORY_CREATED("memory.created"),
    MEMORY_UPDATED("memory.updated"),
    MEMORY_DELETED("memory.deleted"),
    MEMBER_INVITED("member.invited"),
    MEMBER_ACCEPTED("member.accepted"),
    MEMBER_DECLINED("member.declined"),
    MEMBER_REMOVED("member.removed"),
    MEMBER_ROLE_UPDATED("member.roleUpdated"),
    MEMBER_GUEST_UPDATED("member.guestUpdated"),
    COMMENT_CREATED("comment.created"),
    COMMENT_UPDATED("comment.updated"),
    COMMENT_DELETED("comment.deleted"),
    COMMENT_REACTED("comment.reacted");

    private final String wireValue;

    TripRealtimeEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
