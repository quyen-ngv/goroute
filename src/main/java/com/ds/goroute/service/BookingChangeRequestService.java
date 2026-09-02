package com.ds.goroute.service;

import com.ds.goroute.dto.request.BookingChangeRequests;
import com.ds.goroute.dto.response.BookingChangeRequestResponse;
import com.ds.goroute.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Guest-initiated changes to a booking (dates/guests) or order (slot). A request never touches the
 * booking by itself: the partner accepts, and only then the booking is re-priced and its inventory moved.
 */
public interface BookingChangeRequestService {
    BookingChangeRequestResponse requestHotelChange(UUID userId, UUID bookingId, BookingChangeRequests.CreateHotelChange request);
    BookingChangeRequestResponse requestActivityChange(UUID userId, UUID orderId, BookingChangeRequests.CreateActivityChange request);
    List<BookingChangeRequestResponse> listForHotelBooking(UUID actorUserId, UUID bookingId, boolean partner);
    List<BookingChangeRequestResponse> listForActivityOrder(UUID actorUserId, UUID orderId, boolean partner);
    BookingChangeRequestResponse withdraw(UUID userId, UUID requestId);
    BookingChangeRequestResponse decide(UUID actorUserId, UUID requestId, BookingChangeRequests.Decide decision);
    PageResponse<BookingChangeRequestResponse> listForOrganization(UUID actorUserId, UUID organizationId, String status, int page, int size);
    long countOpen(UUID organizationId);
}
