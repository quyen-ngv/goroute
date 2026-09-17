package com.ds.goroute.type;

/** Why a rate cannot be booked for the requested stay; the app localizes it. */
public enum HotelOfferUnavailableReason {
    /** The party does not fit the requested number of rooms. */
    CAPACITY,
    /** Some night has no inventory or no price calendar for this rate. */
    NOT_OPEN,
    SOLD_OUT,
    ARRIVAL_CLOSED,
    DEPARTURE_CLOSED,
    STAY_LENGTH,
    BOOKING_WINDOW
}
