package com.ds.goroute.type;

public enum ActivityProductType {
    TOUR,
    ATTRACTION,
    TRANSFER,
    CLASS,
    RENTAL,
    EVENT,
    PASS,
    OTHER;

    /** Entry tickets get the ticket detail page; every other type uses the tour page. */
    public boolean isTicket() {
        return this == ATTRACTION || this == EVENT || this == PASS;
    }

    /** {@code TICKET} or {@code TOUR}; unknown stored values fall back to the tour page. */
    public static String kindOf(String activityType) {
        try {
            return activityType != null && valueOf(activityType).isTicket() ? "TICKET" : "TOUR";
        } catch (IllegalArgumentException ex) {
            return "TOUR";
        }
    }
}
