package com.ds.goroute.dto.response;

import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.type.SocialSearchType;
import lombok.Builder;
import lombok.Data;

/**
 * One Feed search result, by {@link #type}: {@code USER} → {@link #user}, {@code CHECKIN} →
 * {@link #checkin}, {@code TRIP} → {@link #trip}, {@code PLACE} and {@code STAY} → {@link #place},
 * {@code TOUR} → {@link #activity} (marketplace) or {@link #activityBooking} (catalogue),
 * {@code EVENT} → {@link #event}. Each carries the payload of the list card the app already draws
 * for that kind.
 */
@Data
@Builder
public class SocialSearchItemResponse {
    /** Never {@code ALL}. */
    private SocialSearchType type;
    private DiscoverUserResponse user;
    private UserCheckinResponse checkin;
    private PublicTripResponse trip;
    private PlaceResponse place;
    private MarketplaceActivityResponse activity;
    private ActivityBookingResponse activityBooking;
    private LocalEventSummaryResponse event;
}
