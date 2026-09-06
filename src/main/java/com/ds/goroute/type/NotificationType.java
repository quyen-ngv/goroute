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
    TRIP_INVITE_DECLINED,
    TRIP_INVITE_CANCELLED,
    MEMBER_ADDED,
    MEMBER_INVITED,
    MEMBER_JOIN_REQUESTED,
    MEMBER_ACCESS_GRANTED,
    MEMBER_JOIN_REJECTED,
    MEMBER_JOINED,
    MEMBER_ACCEPTED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    MEMBER_ROLE_UPDATED,
    GUEST_UPDATED,
    GUEST_LINKED,
    
    // Expense events
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    
    // Payment events
    PAYMENT_MARKED,
    PAYMENT_ALL_MARKED,
    PAYMENT_TRIP_MARKED,
    PAYMENT_REMINDER,
    
    // Check-in events
    CHECKIN,
    CHECKIN_UPDATED,
    
    // Note events
    NOTE_ADDED,
    NOTE_UPDATED,
    NOTE_DELETED,

    // Trip memory events
    MEMORY_ADDED,
    MEMORY_DELETED,

    // Travel book events
    TRIP_BOOK_UPDATED,
    
    // Comment events
    COMMENT_ADDED,
    COMMENT_DELETED,

    // Social interactions on public posts. These are aggregated per recipient and
    // target to prevent a busy post from producing a push for every single action.
    SOCIAL_LIKE,
    SOCIAL_COMMENT,
    
    // Other
    ROUTE_OPTIMIZED,
    TRIP_REMINDER,
    TRIP_CLONED,

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

    // Social place extraction
    SOCIAL_PLACES_EXTRACTED,
    AI_TRIP_CREATED,
    
    // Admin notifications
    ADMIN_ANNOUNCEMENT,
    ADMIN_MESSAGE,
    MARKETPLACE_BOOKING_REQUEST,
    MARKETPLACE_BOOKING_CONFIRMED,
    MARKETPLACE_BOOKING_DECLINED,
    // Sent to the guest when the partner did not answer within the hold window (job).
    MARKETPLACE_BOOKING_EXPIRED,
    // Sent to the partner team when the guest withdraws a request or cancels a confirmed booking.
    MARKETPLACE_BOOKING_CANCELLED_BY_GUEST,
    // Sent to the guest for the remaining lifecycle steps: checked in, completed, no-show, platform cancel.
    MARKETPLACE_BOOKING_UPDATED,
    // Guest asked to change dates/guests/slot; sent to the partner team.
    MARKETPLACE_CHANGE_REQUESTED,
    // Partner accepted or declined the change; sent to the guest.
    MARKETPLACE_CHANGE_ANSWERED,
    // Stay / visit completed; invite the guest to review.
    MARKETPLACE_REVIEW_INVITE,
    // Partner organization verification decided by an operator; sent to the owner.
    PARTNER_VERIFICATION_DECIDED,
    // A partner submitted verification documents; sent to operators (optional).
    PARTNER_VERIFICATION_SUBMITTED,
    // Monthly commission statement issued; sent to the owner and finance members.
    PARTNER_STATEMENT_READY
}
