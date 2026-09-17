package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.BookingChangeRequests;
import com.ds.goroute.entity.BookingChangeRequest;
import com.ds.goroute.entity.HotelBooking;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.BookingChangeRequestMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingChangeRequestServiceImplTest {
    private final BookingChangeRequestMapper mapper = mock(BookingChangeRequestMapper.class);
    private final HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
    private final ActivityCommerceRepository activities = mock(ActivityCommerceRepository.class);
    private final HotelMarketplaceService hotelService = mock(HotelMarketplaceService.class);
    private final ActivityCommerceService activityService = mock(ActivityCommerceService.class);
    private final PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MarketplaceHistoryService history = mock(MarketplaceHistoryService.class);
    private final org.springframework.transaction.PlatformTransactionManager transactions =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final BookingChangeRequestServiceImpl service = new BookingChangeRequestServiceImpl(
            mapper, hotels, activities, hotelService, activityService, authorization, notifications, history, transactions);

    private HotelBooking booking(UUID userId, String status) {
        return HotelBooking.builder().id(UUID.randomUUID()).userId(userId).organizationId(UUID.randomUUID()).hotelId(UUID.randomUUID())
                .bookingCode("HTL-1").checkInDate(LocalDate.of(2026, 9, 10)).checkOutDate(LocalDate.of(2026, 9, 12)).adults(2).children(0)
                .bookingStatus(status).totalAmount(new BigDecimal("2000000")).currency("VND").dataVersion(3L).build();
    }

    @Test
    void onlyTheGuestCanAskForAChange() {
        HotelBooking b = booking(UUID.randomUUID(), "CONFIRMED");
        when(hotels.findBooking(b.getId())).thenReturn(java.util.Optional.of(b));
        BookingChangeRequests.CreateHotelChange r = new BookingChangeRequests.CreateHotelChange();
        r.setCheckInDate(LocalDate.of(2026, 9, 11)); r.setCheckOutDate(LocalDate.of(2026, 9, 13));
        assertThrows(BusinessException.class, () -> service.requestHotelChange(UUID.randomUUID(), b.getId(), r));
        verify(mapper, never()).insert(any());
    }

    @Test
    void requestIsQuotedAndPartnerIsNotified() {
        UUID guest = UUID.randomUUID();
        HotelBooking b = booking(guest, "CONFIRMED");
        when(hotels.findBooking(b.getId())).thenReturn(java.util.Optional.of(b));
        when(hotelService.quoteStayChange(eq(b.getId()), any(), any(), anyInt(), anyInt()))
                .thenReturn(new HotelMarketplaceService.StayQuote(true, null, new BigDecimal("2500000"), "VND", 2));
        when(authorization.notificationRecipients(any(), any(), any(), any(), any())).thenReturn(List.of(UUID.randomUUID()));
        when(mapper.findById(any())).thenAnswer(inv -> BookingChangeRequest.builder().id(inv.getArgument(0)).bookingType("HOTEL")
                .hotelBookingId(b.getId()).status("REQUESTED").priceBefore(b.getTotalAmount()).priceAfter(new BigDecimal("2500000")).build());
        BookingChangeRequests.CreateHotelChange r = new BookingChangeRequests.CreateHotelChange();
        r.setCheckInDate(LocalDate.of(2026, 9, 11)); r.setCheckOutDate(LocalDate.of(2026, 9, 13));

        var response = service.requestHotelChange(guest, b.getId(), r);

        assertEquals("REQUESTED", response.getStatus());
        assertEquals(0, new BigDecimal("2500000").compareTo(response.getPriceAfter()));
        verify(mapper).insert(any());
        verify(notifications).createNotification(any(), any(), eq(com.ds.goroute.type.NotificationType.MARKETPLACE_CHANGE_REQUESTED), any(), any(), any(), any());
    }

    @Test
    void decliningRequiresANote() {
        UUID requestId = UUID.randomUUID();
        when(mapper.findById(requestId)).thenReturn(BookingChangeRequest.builder().id(requestId).bookingType("HOTEL").status("REQUESTED").build());
        BookingChangeRequests.Decide decision = new BookingChangeRequests.Decide();
        decision.setAccept(false);
        assertThrows(BusinessException.class, () -> service.decide(UUID.randomUUID(), requestId, decision));
        verify(hotelService, never()).partnerApplyStayChange(any(), any(), any(), any(), any(), any(), any());
    }
}
