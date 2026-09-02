package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Numbers behind the partner dashboard. Every counter is null when the caller lacks the permission
 * that would let them open the underlying list, so the console can hide the tile instead of showing 0.
 * Counters are organization-wide; per-resource scopes narrow the lists, not these totals.
 */
@Value
@Builder
public class PartnerOrganizationSummaryResponse {
    UUID organizationId;
    /** "Today" in the organization timezone; the arrival/departure counters are for this date. */
    LocalDate today;
    String timezone;
    String verificationStatus;
    String operationalStatus;
    /** True when products of this organization can be listed publicly (VERIFIED + ENABLED). */
    boolean bookable;

    Long hotelsTotal;
    Long hotelsEnabled;
    Long activitiesTotal;
    Long activitiesEnabled;

    Long pendingHotelBookings;
    Long pendingActivityOrders;
    Long arrivalsToday;
    Long departuresToday;
    Long inHouse;
    Long activitiesStartingToday;
    Long confirmedHotelBookings;
    Long confirmedActivityOrders;

    Long pendingChangeRequests;
    Long openConversations;
    Long unreadConversations;
    Long unansweredReviews;
}
