package com.ds.goroute.type;

/** Which product line a scheduled message rule covers. */
public enum MarketplaceScheduledMessageAudience {
    HOTEL,
    ACTIVITY,
    ALL;

    public boolean includesHotels() { return this != ACTIVITY; }
    public boolean includesActivities() { return this != HOTEL; }
}
