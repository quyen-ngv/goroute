package com.ds.goroute.service;

import com.ds.goroute.dto.response.IcalFeedLinkResponse;

import java.util.UUID;

/** Per-room iCal export: partner-facing link management and the public feed rendering. */
public interface IcalFeedService {

    /** Returns the room's feed link, creating the token on first use. Requires HOTEL_READ on the hotel. */
    IcalFeedLinkResponse getFeedLink(UUID actorUserId, UUID roomTypeId);

    /** Issues a new token; the previous URL stops working immediately. Requires INVENTORY_WRITE. */
    IcalFeedLinkResponse rotateFeedLink(UUID actorUserId, UUID roomTypeId);

    /** Renders the VCALENDAR for the room behind {@code token}; 404 when the token is unknown. */
    String renderFeed(String token);
}
