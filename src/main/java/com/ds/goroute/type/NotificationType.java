package com.ds.goroute.type;

public enum NotificationType {
    // Trip events
    TRIP_UPDATED,
    TRIP_DELETED,
    
    // Activity events
    ACTIVITY_ADDED,
    ACTIVITY_UPDATED,
    ACTIVITY_DELETED,
    
    // Member events
    TRIP_INVITE,
    MEMBER_ADDED,
    MEMBER_JOINED,
    MEMBER_ACCEPTED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    GUEST_LINKED,
    
    // Expense events
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    
    // Payment events
    PAYMENT_MARKED,
    PAYMENT_ALL_MARKED,
    PAYMENT_TRIP_MARKED,
    
    // Check-in events
    CHECKIN,
    
    // Note events
    NOTE_ADDED,
    NOTE_DELETED,
    
    // Comment events
    COMMENT_ADDED,
    COMMENT_DELETED,
    
    // Other
    ROUTE_OPTIMIZED,
    TRIP_REMINDER,

    // Scheduled itinerary notifications
    TRIP_STARTS_IN_ONE_WEEK,
    TRIP_STARTS_IN_THREE_DAYS,
    TRIP_STARTS_IN_TWO_DAYS,
    TRIP_STARTS_IN_ONE_DAY,
    TRIP_STARTS_IN_TWO_HOURS,
    TRIP_STARTED,
    ITINERARY_ITEM_PREPARATION,
    ITINERARY_ITEM_UPCOMING,
    ITINERARY_ITEM_COMPLETED,
    TRIP_ENDED,
    TRIP_SUMMARY,
    
    // Admin notifications
    ADMIN_ANNOUNCEMENT,
    ADMIN_MESSAGE,
    MARKETPLACE_BOOKING_REQUEST,
    MARKETPLACE_BOOKING_CONFIRMED,
    MARKETPLACE_BOOKING_DECLINED
}
