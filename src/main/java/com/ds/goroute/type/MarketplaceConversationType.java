package com.ds.goroute.type;

public enum MarketplaceConversationType {
    DIRECT,
    HOTEL_BOOKING,
    ACTIVITY_ORDER,
    /** The group chat of one trip; its members are the trip's members. */
    TRIP;

    /**
     * Threads nobody but their participants may read: the trip group and person-to-person
     * chat. A thread about a booking is commercial, can be disputed, and stays readable by
     * an operator; these two are not and do not.
     *
     * <p>Keyed off the type rather than checked at each call site so that a new admin
     * endpoint cannot forget the rule.
     */
    public boolean isPrivate(java.util.UUID organizationId) {
        return this == TRIP || (this == DIRECT && organizationId == null);
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
