package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateGuideBookingRequest;
import com.ds.goroute.dto.response.GuideBookingResponse;
import com.ds.goroute.entity.GuidePayoutEntry;
import com.ds.goroute.entity.GuideReview;
import com.ds.goroute.type.GuideBookingStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Booking a guide, and the money that follows (GUIDE-04, GUIDE-05, GUIDE-08).
 *
 * <p>Two rules about money hold throughout:
 * <ul>
 *   <li>The guide is paid only after the service is done and the complaint window has
 *       passed. Paying earlier and then discovering a dispute leaves no way back.</li>
 *   <li>Financial history is never edited. A correction is a new entry pointing at the
 *       one it corrects.</li>
 * </ul>
 */
public interface GuideBookingService {

    /** Requests a booking. Repeating the same key returns the booking already made. */
    GuideBookingResponse request(UUID travelerId, CreateGuideBookingRequest request);

    /** The guide accepts or declines. Not answering in time lets the request lapse. */
    GuideBookingResponse respond(UUID guideUserId, UUID bookingId, boolean accept, String reason);

    GuideBookingResponse confirm(UUID travelerId, UUID bookingId);

    GuideBookingResponse cancel(UUID actorId, UUID bookingId, String reason);

    GuideBookingResponse complete(UUID guideUserId, UUID bookingId);

    /** Opens a dispute, which freezes the payout until somebody resolves it. */
    GuideBookingResponse openDispute(UUID actorId, UUID bookingId, String reason);

    GuideBookingResponse get(UUID actorId, UUID bookingId);

    List<GuideBookingResponse> forGuide(UUID guideUserId, GuideBookingStatus status, int page, int size);

    List<GuideBookingResponse> forTraveler(UUID travelerId, int page, int size);

    /** Only somebody whose booking was completed can review it. */
    GuideReview review(UUID travelerId, UUID bookingId, int rating, String comment);

    GuideReview respondToReview(UUID guideUserId, UUID reviewId, String response);

    List<GuideReview> reviewsForGuide(UUID guideId, int page, int size);

    /** What the guide's own report shows, computed from bookings rather than declared. */
    Map<String, Object> performance(UUID guideUserId, int days);

    List<GuidePayoutEntry> payoutLedger(UUID guideUserId, int page, int size);

    /** Lapses requests the guide never answered. Driven by a scheduled job. */
    int expireStaleRequests(int batchSize);

    /** Releases payouts that are past the complaint window and not frozen. */
    int releaseDuePayouts(int batchSize);

    /** Operator hold, used while a dispute is being looked at. */
    void freezePayout(UUID operatorId, UUID bookingId, boolean frozen);
}
