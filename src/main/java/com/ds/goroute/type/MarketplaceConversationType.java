package com.ds.goroute.type;

public enum MarketplaceConversationType {
    DIRECT,
    HOTEL_BOOKING,
    ACTIVITY_ORDER,
    /** The group chat of one trip; its members are the trip's members. */
    TRIP,
    /** The group chat of one meetup; its members are the host and every JOINED participant. */
    MEETUP,
    /** The group chat of one local event; its members are everybody going to it. */
    EVENT,
    /** One person's private chat with the assistant (Pengoo); see {@code assistant_user_id}. */
    ASSISTANT;

    /**
     * Threads nobody but their participants may read: the trip, meetup and event groups and
     * person-to-person chat. A thread about a booking is commercial, can be disputed, and stays readable by
     * an operator; these are not and do not.
     *
     * <p>Keyed off the type rather than checked at each call site so that a new admin
     * endpoint cannot forget the rule.
     */
    public boolean isPrivate(java.util.UUID organizationId) {
        return this == TRIP || this == MEETUP || this == EVENT || this == ASSISTANT
                || (this == DIRECT && organizationId == null);
    }

    public static boolean isPrivate(String conversationType, java.util.UUID organizationId) {
        for (MarketplaceConversationType value : values()) {
            if (value.name().equals(conversationType)) {
                return value.isPrivate(organizationId);
            }
        }
        return false;
    }
}
