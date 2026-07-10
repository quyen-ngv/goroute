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
    
    // Admin notifications
    ADMIN_ANNOUNCEMENT,
    ADMIN_MESSAGE
}
