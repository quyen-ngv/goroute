package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Check-in: somebody was somewhere, with photos and a thought (epic 06).
 *
 * <p>One flow, two records. Every submission creates a new check-in, because visiting a
 * place twice is two moments. A rating additionally creates or updates that person's
 * single review of the place, because an opinion is one per person and revisiting means
 * changing your mind, not holding two opinions at once. The place average is computed from
 * reviews only -- counting check-ins would let one regular move a café's score tenfold.
 */
public interface UserCheckinService {

    /**
     * What the composer should show once a place is chosen: the author's existing rating
     * if any, which controls to show, and the limits currently configured.
     */
    CheckinContextResponse context(UUID userId, UUID placeId, String locationKey);

    /**
     * Creates a check-in, and creates or updates the author's review when they rated a
     * catalogued place. Both happen in one transaction: a saved check-in whose rating went
     * missing is worse than a failed submission.
     */
    UserCheckinResponse create(UUID userId, CreateUserCheckinRequest request);

    /** Edits caption, rating, custom name and visibility. The place cannot be changed. */
    UserCheckinResponse update(UUID userId, UUID checkinId, UpdateUserCheckinRequest request);

    /**
     * Hides the check-in. The author's review of the place survives, and the place average
     * does not move: deleting one bad photo from last year should not silently retract an
     * opinion nobody meant to touch.
     */
    void delete(UUID userId, UUID checkinId);

    UserCheckinResponse get(UUID viewerId, UUID checkinId);

    CheckinLikeResponse toggleLike(UUID userId, UUID checkinId);

    /** Public feed, newest first, paged on the timestamp of the last row seen. */
    List<UserCheckinResponse> feed(UUID viewerId, LocalDateTime before, int limit);

    List<UserCheckinResponse> byUser(UUID viewerId, UUID userId, int page, int size);

    List<UserCheckinResponse> byPlace(UUID placeId, int page, int size);

    /** Everything at one uncatalogued spot, so its name is still tappable in the feed. */
    List<UserCheckinResponse> byLocationKey(String locationKey, int page, int size);
}
