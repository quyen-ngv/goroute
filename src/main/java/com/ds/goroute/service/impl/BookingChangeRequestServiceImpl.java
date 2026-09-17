package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.BookingChangeRequests;
import com.ds.goroute.dto.response.BookingChangeRequestResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.ActivityOrder;
import com.ds.goroute.entity.BookingChangeRequest;
import com.ds.goroute.entity.HotelBooking;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.MarketplaceActivityProduct;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.BookingChangeRequestMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BookingChangeRequestService;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.type.BookingChangeRequestStatus;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingChangeRequestServiceImpl implements BookingChangeRequestService {
    private final BookingChangeRequestMapper mapper;
    private final HotelMarketplaceRepository hotelRepository;
    private final ActivityCommerceRepository activityRepository;
    private final HotelMarketplaceService hotelService;
    private final ActivityCommerceService activityService;
    private final PartnerAuthorizationService authorization;
    private final NotificationService notificationService;
    private final MarketplaceHistoryService history;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public BookingChangeRequestResponse requestHotelChange(UUID userId, UUID bookingId, BookingChangeRequests.CreateHotelChange r) {
        HotelBooking b = hotelRepository.findBooking(bookingId).orElseThrow(() -> notFound("Hotel booking not found"));
        if (!userId.equals(b.getUserId())) throw forbidden();
        requireChangeable(b.getBookingStatus());
        if (!r.getCheckOutDate().isAfter(r.getCheckInDate())) throw bad("checkOutDate must be after checkInDate");
        int adults = r.getAdults() == null ? b.getAdults() : r.getAdults();
        int children = r.getChildren() == null ? b.getChildren() : r.getChildren();
        if (r.getCheckInDate().equals(b.getCheckInDate()) && r.getCheckOutDate().equals(b.getCheckOutDate())
                && adults == b.getAdults() && children == b.getChildren()) throw bad("Nothing changes in this request");
        // Quote now so the partner sees the price impact; availability is re-checked again on accept.
        HotelMarketplaceService.StayQuote quote = hotelService.quoteStayChange(bookingId, r.getCheckInDate(), r.getCheckOutDate(), adults, children);
        if (!quote.available()) throw conflict("The requested dates are not available: " + quote.reason());
        BookingChangeRequest request = BookingChangeRequest.builder().id(UUID.randomUUID()).bookingType("HOTEL").hotelBookingId(b.getId())
                .organizationId(b.getOrganizationId()).requestedBy(userId).newCheckInDate(r.getCheckInDate()).newCheckOutDate(r.getCheckOutDate())
                .newAdults(adults).newChildren(children).message(trim(r.getMessage())).status(BookingChangeRequestStatus.REQUESTED.name())
                .priceBefore(b.getTotalAmount()).priceAfter(quote.total()).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        insert(request);
        history.record(b.getOrganizationId(), "HOTEL_BOOKING", b.getId(), "CHANGE_REQUESTED", request, List.of("changeRequest"), userId, "USER", request.getMessage());
        notifyPartner(b.getOrganizationId(), "HOTEL", b.getHotelId(), "BOOKING_READ", b.getBookingCode(), b.getId(), "bookingId", request.getId(), userId);
        return response(request.getId());
    }

    @Override
    @Transactional
    public BookingChangeRequestResponse requestActivityChange(UUID userId, UUID orderId, BookingChangeRequests.CreateActivityChange r) {
        ActivityOrder o = activityRepository.findOrder(orderId).orElseThrow(() -> notFound("Activity order not found"));
        if (!userId.equals(o.getUserId())) throw forbidden();
        requireChangeable(o.getOrderStatus());
        if (r.getSlotId().equals(o.getSlotId())) throw bad("The order already uses this slot");
        ActivityCommerceService.SlotQuote quote = activityService.quoteSlotChange(orderId, r.getSlotId());
        if (!quote.available()) throw conflict("The requested slot is not available: " + quote.reason());
        BookingChangeRequest request = BookingChangeRequest.builder().id(UUID.randomUUID()).bookingType("ACTIVITY").activityOrderId(o.getId())
                .organizationId(o.getOrganizationId()).requestedBy(userId).newSlotId(r.getSlotId()).message(trim(r.getMessage()))
                .status(BookingChangeRequestStatus.REQUESTED.name()).priceBefore(o.getTotalAmount()).priceAfter(quote.total())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        insert(request);
        history.record(o.getOrganizationId(), "ACTIVITY_ORDER", o.getId(), "CHANGE_REQUESTED", request, List.of("changeRequest"), userId, "USER", request.getMessage());
        notifyPartner(o.getOrganizationId(), "ACTIVITY", o.getActivityBookingId(), "ORDER_READ", o.getOrderCode(), o.getId(), "orderId", request.getId(), userId);
        return response(request.getId());
    }

    @Override
    public List<BookingChangeRequestResponse> listForHotelBooking(UUID actor, UUID bookingId, boolean partner) {
        HotelBooking b = hotelRepository.findBooking(bookingId).orElseThrow(() -> notFound("Hotel booking not found"));
        if (partner) authorization.requireResourcePermission(b.getOrganizationId(), actor, "HOTEL", b.getHotelId(), "BOOKING_READ");
        else if (!actor.equals(b.getUserId())) throw forbidden();
        return mapper.findForHotelBooking(bookingId).stream().map(BookingChangeRequestResponse::from).toList();
    }

    @Override
    public List<BookingChangeRequestResponse> listForActivityOrder(UUID actor, UUID orderId, boolean partner) {
        ActivityOrder o = activityRepository.findOrder(orderId).orElseThrow(() -> notFound("Activity order not found"));
        if (partner) authorization.requireResourcePermission(o.getOrganizationId(), actor, "ACTIVITY", o.getActivityBookingId(), "ORDER_READ");
        else if (!actor.equals(o.getUserId())) throw forbidden();
        return mapper.findForActivityOrder(orderId).stream().map(BookingChangeRequestResponse::from).toList();
    }

    @Override
    @Transactional
    public BookingChangeRequestResponse withdraw(UUID userId, UUID requestId) {
        BookingChangeRequest request = required(requestId);
        if (!userId.equals(request.getRequestedBy())) throw forbidden();
        if (mapper.updateStatus(requestId, BookingChangeRequestStatus.REQUESTED.name(), BookingChangeRequestStatus.WITHDRAWN.name(), userId, null, null, LocalDateTime.now()) != 1)
            throw conflict("This request is no longer open");
        return response(requestId);
    }

    @Override
    @Transactional
    public BookingChangeRequestResponse decide(UUID actor, UUID requestId, BookingChangeRequests.Decide decision) {
        BookingChangeRequest request = required(requestId);
        if (!BookingChangeRequestStatus.REQUESTED.name().equals(request.getStatus())) throw conflict("This request was already answered");
        String note = trim(decision.getNote());
        if (!decision.isAccept() && note == null) throw bad("A note is required when declining a change request");
        BigDecimal priceAfter = null;
        UUID guestId = request.getRequestedBy();
        String code = request.getBookingCode();
        if ("HOTEL".equals(request.getBookingType())) {
            HotelBooking b = hotelRepository.findBooking(request.getHotelBookingId()).orElseThrow(() -> notFound("Hotel booking not found"));
            authorization.requireResourcePermission(b.getOrganizationId(), actor, "HOTEL", b.getHotelId(), "BOOKING_WRITE");
            if (isTerminal(b.getBookingStatus())) { expire(request); throw conflict("The booking is no longer active; the request expired"); }
            if (decision.isAccept()) {
                priceAfter = hotelService.partnerApplyStayChange(actor, b.getId(), request.getNewCheckInDate(), request.getNewCheckOutDate(),
                        request.getNewAdults(), request.getNewChildren(), decision.getExpectedVersion() == null ? b.getDataVersion() : decision.getExpectedVersion()).getTotalAmount();
            }
        } else {
            ActivityOrder o = activityRepository.findOrder(request.getActivityOrderId()).orElseThrow(() -> notFound("Activity order not found"));
            authorization.requireResourcePermission(o.getOrganizationId(), actor, "ACTIVITY", o.getActivityBookingId(), "ORDER_WRITE");
            if (isTerminal(o.getOrderStatus())) { expire(request); throw conflict("The order is no longer active; the request expired"); }
            if (decision.isAccept()) {
                priceAfter = activityService.partnerApplySlotChange(actor, o.getId(), request.getNewSlotId(),
                        decision.getExpectedVersion() == null ? o.getDataVersion() : decision.getExpectedVersion()).getTotalAmount();
            }
        }
        String target = decision.isAccept() ? BookingChangeRequestStatus.ACCEPTED.name() : BookingChangeRequestStatus.DECLINED.name();
        if (mapper.updateStatus(requestId, BookingChangeRequestStatus.REQUESTED.name(), target, actor, note, priceAfter, LocalDateTime.now()) != 1)
            throw conflict("This request was answered by someone else");
        String label = decision.isAccept() ? "accepted" : "declined";
        Map<String, Object> data = Map.of(
                "HOTEL".equals(request.getBookingType()) ? "bookingId" : "orderId", "HOTEL".equals(request.getBookingType()) ? request.getHotelBookingId().toString() : request.getActivityOrderId().toString(),
                "bookingCode", code == null ? "" : code, "decisionLabel", label, "changeRequestId", requestId.toString(),
                "deepLink", "HOTEL".equals(request.getBookingType()) ? "/marketplace/orders/hotel/" + request.getHotelBookingId() : "/marketplace/orders/activity/" + request.getActivityOrderId());
        notificationService.createNotification(guestId, null, NotificationType.MARKETPLACE_CHANGE_ANSWERED,
                "Change request " + label, (code == null ? "" : code + ": ") + (note == null ? "" : note), data, actor);
        return response(requestId);
    }

    @Override
    public PageResponse<BookingChangeRequestResponse> listForOrganization(UUID actor, UUID organizationId, String status, int page, int size) {
        authorization.requireOrganization(organizationId, actor);
        List<UUID> hotelIds = authorization.accessibleResourceIds(organizationId, actor, "HOTEL",
                hotelRepository.findHotelsByOrganization(organizationId).stream().map(HotelProfile::getId).toList(), "BOOKING_READ");
        List<UUID> productIds = authorization.accessibleResourceIds(organizationId, actor, "ACTIVITY",
                activityRepository.findProductsByOrganization(organizationId).stream().map(MarketplaceActivityProduct::getId).toList(), "ORDER_READ");
        int limit = Math.min(Math.max(size, 1), 200); int offset = Math.max(page, 0) * limit;
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        long total = mapper.countForOrganization(organizationId, normalized, hotelIds, productIds);
        List<BookingChangeRequestResponse> items = mapper.findForOrganization(organizationId, normalized, hotelIds, productIds, limit, offset)
                .stream().map(BookingChangeRequestResponse::from).toList();
        return PageResponse.of(items, total, Math.max(page, 0), limit);
    }

    @Override
    public long countOpen(UUID organizationId) { return mapper.countOpenForOrganization(organizationId); }

    private void insert(BookingChangeRequest request) {
        try { mapper.insert(request); }
        catch (DataIntegrityViolationException ex) { throw conflict("There is already an open change request for this booking"); }
    }
    /**
     * Expires the request in its own transaction, because every caller throws straight afterwards.
     * Written on the caller's transaction the row would be rolled back with the error and the
     * request would stay REQUESTED for ever, waiting on a booking that can never answer it. The
     * caller still throws, so the response the partner sees is unchanged.
     */
    private void expire(BookingChangeRequest request) {
        TransactionTemplate outOfBand = new TransactionTemplate(transactionManager);
        outOfBand.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outOfBand.executeWithoutResult(status -> mapper.updateStatus(request.getId(), BookingChangeRequestStatus.REQUESTED.name(),
                BookingChangeRequestStatus.EXPIRED.name(), null, "Booking no longer active", null, LocalDateTime.now()));
    }
    private void notifyPartner(UUID orgId, String type, UUID resourceId, String permission, String code, UUID bookingId, String idKey, UUID requestId, UUID guestId) {
        Map<String, Object> data = Map.of(idKey, bookingId.toString(), "bookingCode", code, "changeRequestId", requestId.toString(), "deepLink", "/partner/bookings?tab=changes");
        authorization.notificationRecipients(orgId, type, resourceId, permission, guestId).forEach(userId ->
                notificationService.createNotification(userId, null, NotificationType.MARKETPLACE_CHANGE_REQUESTED, "Change request", code + " — the guest asked for a change", data, guestId));
    }
    private void requireChangeable(String status) {
        MarketplaceBookingStatus current;
        try { current = MarketplaceBookingStatus.valueOf(status); } catch (IllegalArgumentException ex) { throw bad("Invalid booking status"); }
        if (current != MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION && current != MarketplaceBookingStatus.CONFIRMED)
            throw bad("Only pending or confirmed bookings can be changed");
    }
    private boolean isTerminal(String status) {
        try { return MarketplaceBookingStatus.valueOf(status).isTerminal() || MarketplaceBookingStatus.valueOf(status) == MarketplaceBookingStatus.CHECKED_IN; }
        catch (IllegalArgumentException ex) { return true; }
    }
    private BookingChangeRequest required(UUID id) {
        BookingChangeRequest request = mapper.findById(id);
        if (request == null) throw notFound("Change request not found");
        return request;
    }
    private BookingChangeRequestResponse response(UUID id) { return BookingChangeRequestResponse.from(required(id)); }
    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private BusinessException bad(String m) { return new BusinessException(ErrorConstant.BAD_REQUEST, m); }
    private BusinessException conflict(String m) { return new BusinessException(ErrorConstant.ALREADY_PROCESSED, m); }
    private BusinessException notFound(String m) { return new BusinessException(ErrorConstant.NOT_FOUND, m); }
    private BusinessException forbidden() { return new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You cannot access this booking"); }
}
