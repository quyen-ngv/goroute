package com.ds.goroute.type;

/**
 * Stable, version-one names for events on a person's own {@code /topic/users/{userId}/events}.
 *
 * <p>Part of the WebSocket contract. Clients must ignore names they do not understand.
 */
public enum UserRealtimeEventType {
    /** The trip is gone for this person: deleted, or they were removed or left. Payload: tripId, reason. */
    TRIP_ACCESS_REVOKED("trip.accessRevoked"),
    /** The server refused a SUBSCRIBE. Payload: destination. The client should stop watching it. */
    SUBSCRIPTION_DENIED("subscription.denied"),
    /** A notification row was created or changed for this person. Payload: notificationId, type. */
    NOTIFICATION_CHANGED("notification.changed"),
    /**
     * The AI credit balance moved (charge, refund, refill, adjustment). Payload: balance,
     * planBalance, purchasedBalance, delta, type, feature, traceId. Clients set the balance from
     * it directly and refetch after a reconnect.
     */
    CREDIT_CHANGED("credit.changed");

    private final String wireValue;

    UserRealtimeEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
