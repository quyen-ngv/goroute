package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.IcalFeedLinkResponse;
import com.ds.goroute.entity.MarketplaceIcalBooking;
import com.ds.goroute.entity.MarketplaceIcalRoom;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MarketplaceIcalMapper;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.utils.IcalWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IcalFeedServiceImplTest {

    private final MarketplaceIcalMapper mapper = mock(MarketplaceIcalMapper.class);
    private final PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
    private final IcalFeedServiceImpl service = new IcalFeedServiceImpl(mapper, authorization, "https://api.example.com/");

    private final UUID roomId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private MarketplaceIcalRoom room(String token) {
        return MarketplaceIcalRoom.builder().roomTypeId(roomId).hotelId(hotelId).organizationId(orgId)
                .roomName("Deluxe").hotelName("Sea View").icalToken(token).build();
    }

    @Test
    void getFeedLinkLazilyCreatesUrlSafe48CharTokenAndChecksHotelRead() {
        when(mapper.findRoomById(roomId)).thenReturn(room(null));
        when(mapper.assignTokenIfMissing(eq(roomId), anyString())).thenReturn(1);

        IcalFeedLinkResponse link = service.getFeedLink(actor, roomId);

        verify(authorization).requireResourcePermission(orgId, actor, "HOTEL", hotelId, "HOTEL_READ");
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mapper).assignTokenIfMissing(eq(roomId), token.capture());
        assertEquals(48, token.getValue().length());
        assertTrue(token.getValue().matches("^[A-Za-z0-9_-]+$"));
        assertEquals(token.getValue(), link.getToken());
        assertEquals("https://api.example.com/v1/api/public/ical/rooms/" + token.getValue() + ".ics", link.getUrl());
    }

    @Test
    void getFeedLinkReusesExistingTokenWithoutWriting() {
        when(mapper.findRoomById(roomId)).thenReturn(room("existing-token-existing-token-existing-token-1234"));

        IcalFeedLinkResponse link = service.getFeedLink(actor, roomId);

        assertEquals("existing-token-existing-token-existing-token-1234", link.getToken());
        verify(mapper, never()).assignTokenIfMissing(any(), anyString());
        verify(mapper, never()).replaceToken(any(), anyString());
    }

    @Test
    void rotateRequiresInventoryWriteAndIssuesNewToken() {
        when(mapper.findRoomById(roomId)).thenReturn(room("old-token-old-token-old-token-old-token-old-toke"));
        when(mapper.replaceToken(eq(roomId), anyString())).thenReturn(1);

        IcalFeedLinkResponse link = service.rotateFeedLink(actor, roomId);

        verify(authorization).requireResourcePermission(orgId, actor, "HOTEL", hotelId, "INVENTORY_WRITE");
        verify(mapper).replaceToken(eq(roomId), eq(link.getToken()));
        assertNotEquals("old-token-old-token-old-token-old-token-old-toke", link.getToken());
        assertEquals(48, link.getToken().length());
    }

    @Test
    void unknownRoomIs404() {
        when(mapper.findRoomById(roomId)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getFeedLink(actor, roomId));
        assertEquals(ErrorConstant.NOT_FOUND, ex.getError().getCode());
        verify(authorization, never()).requireResourcePermission(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void renderFeedRejectsUnknownAndMalformedTokens() {
        when(mapper.findRoomByToken(anyString())).thenReturn(null);
        assertEquals(ErrorConstant.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.renderFeed("abcdefghijklmnopqrstuvwxyz")).getError().getCode());
        assertEquals(ErrorConstant.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.renderFeed("has spaces and/slashes")).getError().getCode());
        verify(mapper, never()).findRoomByToken("has spaces and/slashes");
    }

    @Test
    void renderFeedEmitsBookingsAndClosedDays() {
        String token = "tok-tok-tok-tok-tok-tok-tok-tok-tok-tok-tok-tok1";
        when(mapper.findRoomByToken(token)).thenReturn(room(token));
        UUID bookingId = UUID.randomUUID();
        when(mapper.findBookedStays(eq(roomId), any(), anyInt())).thenReturn(List.of(
                MarketplaceIcalBooking.builder().bookingId(bookingId).bookingCode("HB-42")
                        .checkInDate(LocalDate.of(2026, 10, 1)).checkOutDate(LocalDate.of(2026, 10, 4)).build()));
        when(mapper.findStopSellDays(eq(roomId), any(), any(), anyInt())).thenReturn(List.of(
                LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 31)));

        String ics = service.renderFeed(token);

        assertTrue(ics.contains("X-WR-CALNAME:Sea View - Deluxe\r\n"));
        assertTrue(ics.contains("UID:" + bookingId + "@goroute\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261001\r\nDTEND;VALUE=DATE:20261004\r\nSUMMARY:Booked - HB-42\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261226\r\nSUMMARY:Closed\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261231\r\nDTEND;VALUE=DATE:20270101\r\nSUMMARY:Closed\r\n"));
        assertEquals(3, ics.split("END:VEVENT").length - 1);
    }

    @Test
    void buildEventsMergesConsecutiveClosedDaysAndFixesDegenerateStays() {
        UUID bookingId = UUID.randomUUID();
        List<IcalWriter.Event> events = IcalFeedServiceImpl.buildEvents(roomId,
                List.of(MarketplaceIcalBooking.builder().bookingId(bookingId).bookingCode("HB-1")
                        .checkInDate(LocalDate.of(2026, 5, 5)).checkOutDate(LocalDate.of(2026, 5, 5)).build()),
                List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                        LocalDate.of(2026, 6, 10)));

        assertEquals(3, events.size());
        assertEquals(LocalDate.of(2026, 5, 6), events.get(0).endExclusive());
        assertEquals("Booked - HB-1", events.get(0).summary());
        assertEquals(LocalDate.of(2026, 6, 1), events.get(1).start());
        assertEquals(LocalDate.of(2026, 6, 4), events.get(1).endExclusive());
        assertEquals(LocalDate.of(2026, 6, 10), events.get(2).start());
        assertEquals(LocalDate.of(2026, 6, 11), events.get(2).endExclusive());
        assertEquals("Closed", events.get(2).summary());
    }
}
