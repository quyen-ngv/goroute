package com.ds.goroute.service;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinContextResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.CheckinLikeResponse;

import java.math.BigDecimal;
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
    CheckinContextResponse context(UUID userId, UUID placeId, String locationKey,
                                   BigDecimal latitude, BigDecimal longitude,
                                   BigDecimal accuracyMeters);

    /** Backwards-compatible context call for non-composer integrations without a GPS preview. */
    default CheckinContextResponse context(UUID userId, UUID placeId, String locationKey) {
        return context(userId, placeId, locationKey, null, null, null);
    }

    /**
     * Creates a check-in, and creates or updates the author's review when they rated a
     * catalogued place. Both happen in one transaction: a saved check-in whose rating went
     * missing is worse than a failed submission.
     */
    UserCheckinResponse create(UUID userId, CreateUserCheckinRequest request);

    /** Edits caption, rating, custom name and visibility. The place cannot be changed. */
    UserCheckinResponse update(UUID userId, UUID checkinId, UpdateUserCheckinRequest request);

    /**
     * Hides the check-in. By default the author's review of the place survives and the place
     * average does not move: deleting one bad photo from last year should not silently
     * retract an opinion nobody meant to touch.
     *
     * <p>{@code deleteReview} exists because the composer is currently the only way to write
     * a place review at all. Without it, dropping the visit would strand the review with no
     * surface that can remove it.
     */
    void delete(UUID userId, UUID checkinId, boolean deleteReview);

    /** Keeps the check-in only, which is what every existing caller meant. */
    default void delete(UUID userId, UUID checkinId) {
        delete(userId, checkinId, false);
    }

    /**
     * The caller's own check-in for an activity, place or trip, if there is one.
     *
     * <p>What makes "have I already checked in here" answerable: a check-in stores the
     * activity and trip it was attached to, so it can be told apart from a visit to the
     * same place on another journey.
     */
    UserCheckinResponse findMine(UUID userId, UUID activityId, UUID placeId, UUID tripId);

    UserCheckinResponse get(UUID viewerId, UUID checkinId);

    CheckinLikeResponse toggleLike(UUID userId, UUID checkinId);

    /**
     * Public feed without the viewer's own check-ins: posts with photos first, then newest
     * first. {@code geo} limits it to an area; null means everywhere.
     *
     * <p>{@code cursor} is the {@code feedCursor} of the last post the client holds; when
     * present the page after it is returned and {@code page} is ignored. Without it the feed
     * pages by number, as builds that predate the cursor expect.
     */
    List<UserCheckinResponse> feed(UUID viewerId, FeedGeoFilter geo, int page, int size, String cursor);

    /**
     * Public check-ins by the people {@code viewerId} follows, newest first. Deleted
     * authors, taken-down posts and anyone blocked in either direction are left out:
     * blocking does not remove a follow, so the follow alone is not consent to be shown.
     */
    /**
     * Public check-ins by the people the viewer follows. With a focus point (both non-null)
     * the ones in its province come first, then the rest by 100 km ring outwards, newest
     * first within each; each carries its {@code focusRank}. Without, newest first.
     */
    List<UserCheckinResponse> followingFeed(UUID viewerId, Double focusLat, Double focusLng, int page, int size);

    List<UserCheckinResponse> byUser(UUID viewerId, UUID userId, int page, int size);

    List<UserCheckinResponse> byPlace(UUID placeId, int page, int size);

    /**
     * Every public check-in written during one trip, oldest first.
     *
     * <p>The shared-trip page draws these under the stops they belong to, which is why
     * they arrive as one list rather than a page: an itinerary is read whole.
     */
    List<UserCheckinResponse> byTrip(UUID viewerId, UUID tripId);

    /** Everything at one uncatalogued spot, so its name is still tappable in the feed. */
    List<UserCheckinResponse> byLocationKey(String locationKey, int page, int size);

    /** Wards this person has checked in at; private check-ins count only for the owner. */
    List<com.ds.goroute.dto.response.VisitedWardResponse> visitedWards(UUID viewerId, UUID userId);
}
