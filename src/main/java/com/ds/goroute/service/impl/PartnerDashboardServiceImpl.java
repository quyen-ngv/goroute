package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PartnerOrganizationSummaryResponse;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.MarketplaceReviewResponseRepository;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.PartnerDashboardService;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerDashboardServiceImpl implements PartnerDashboardService {
    private final PartnerAuthorizationService authorization;
    private final HotelMarketplaceRepository hotelRepository;
    private final ActivityCommerceRepository activityRepository;
    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceReviewResponseRepository reviewRepository;
    private final com.ds.goroute.service.BookingChangeRequestService changeRequests;

    @Override
    public PartnerOrganizationSummaryResponse summary(UUID actor, UUID organizationId) {
        HostOrganization org = authorization.requireOrganization(organizationId, actor);
        ZoneId zone;
        try { zone = ZoneId.of(org.getTimezone()); } catch (Exception ex) { zone = ZoneId.systemDefault(); }
        LocalDate today = LocalDate.now(zone);
        boolean hotels = authorization.hasPermission(organizationId, actor, "HOTEL_READ");
        boolean activities = authorization.hasPermission(organizationId, actor, "ACTIVITY_READ");
        boolean bookings = authorization.hasPermission(organizationId, actor, "BOOKING_READ");
        boolean orders = authorization.hasPermission(organizationId, actor, "ORDER_READ");
        boolean chat = authorization.hasPermission(organizationId, actor, "CHAT_WRITE");
        boolean reviews = authorization.hasPermission(organizationId, actor, "REVIEW_RESPOND");
        String enabled = MarketplacePublicationStatus.ENABLED.name();
        String pending = MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name();
        String confirmed = MarketplaceBookingStatus.CONFIRMED.name();

        PartnerOrganizationSummaryResponse.PartnerOrganizationSummaryResponseBuilder out = PartnerOrganizationSummaryResponse.builder()
                .organizationId(organizationId).today(today).timezone(zone.getId())
                .verificationStatus(org.getVerificationStatus()).operationalStatus(org.getOperationalStatus())
                .bookable(OrganizationVerificationStatus.VERIFIED.name().equals(org.getVerificationStatus())
                        && OrganizationOperationalStatus.ENABLED.name().equals(org.getOperationalStatus()));
        if (hotels) {
            var list = hotelRepository.findHotelsByOrganization(organizationId);
            out.hotelsTotal((long) list.size()).hotelsEnabled(list.stream().filter(h -> enabled.equals(h.getStatus())).count());
        }
        if (activities) {
            var list = activityRepository.findProductsByOrganization(organizationId);
            out.activitiesTotal((long) list.size()).activitiesEnabled(list.stream().filter(p -> enabled.equals(p.getProductStatus())).count());
        }
        if (bookings) {
            out.pendingHotelBookings(hotelRepository.countBookingsByOrganization(organizationId, pending))
               .confirmedHotelBookings(hotelRepository.countBookingsByOrganization(organizationId, confirmed))
               .arrivalsToday(hotelRepository.countArrivals(organizationId, today))
               .departuresToday(hotelRepository.countDepartures(organizationId, today))
               .inHouse(hotelRepository.countInHouse(organizationId));
        }
        if (orders) {
            out.pendingActivityOrders(activityRepository.countOrdersByOrganization(organizationId, pending))
               .confirmedActivityOrders(activityRepository.countOrdersByOrganization(organizationId, confirmed))
               .activitiesStartingToday(activityRepository.countOrdersStartingOn(organizationId, today));
        }
        if (bookings || orders) out.pendingChangeRequests(changeRequests.countOpen(organizationId));
        if (chat) {
            out.openConversations(chatRepository.countOpenForOrganization(organizationId))
               .unreadConversations(chatRepository.countUnreadForOrganization(organizationId, actor));
        }
        if (reviews) {
            out.unansweredReviews(reviewRepository.countUnansweredByOrganization(organizationId));
        }
        return out.build();
    }
}
