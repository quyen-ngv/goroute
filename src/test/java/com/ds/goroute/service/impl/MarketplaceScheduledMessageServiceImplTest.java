package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.StartMarketplaceConversationRequest;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.MarketplaceScheduledMessageRun;
import com.ds.goroute.entity.ScheduledMessageTarget;
import com.ds.goroute.repository.MarketplaceScheduledMessageRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MarketplaceScheduledMessageRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MarketplaceScheduledMessageServiceImplTest {
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 8, 0);

    private MarketplaceScheduledMessageRepository repository;
    private MarketplaceChatService chatService;
    private MarketplaceScheduledMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(MarketplaceScheduledMessageRepository.class);
        chatService = mock(MarketplaceChatService.class);
        service = new MarketplaceScheduledMessageServiceImpl(repository,
                mock(PartnerAuthorizationService.class), chatService);
    }

    private static MarketplaceScheduledMessage rule(String trigger, String appliesTo, int offsetHours) {
        return MarketplaceScheduledMessage.builder()
                .id(UUID.randomUUID()).organizationId(ORG).triggerType(trigger).appliesTo(appliesTo)
                .offsetHours(offsetHours).body("Chào {guestName}, mã đơn {bookingCode}.").status("ENABLED")
                .build();
    }

    private static ScheduledMessageTarget stay() {
        return ScheduledMessageTarget.builder()
                .bookingType("HOTEL").hotelBookingId(BOOKING).organizationId(ORG).ownerUserId(OWNER)
                .guestUserId(UUID.randomUUID()).bookingCode("HB-1").guestName("Lan").propertyName("Biển Xanh")
                .checkInDate(LocalDate.of(2026, 9, 2)).checkOutDate(LocalDate.of(2026, 9, 4))
                .status("CONFIRMED").build();
    }

    private static ScheduledMessageTarget visit() {
        return ScheduledMessageTarget.builder()
                .bookingType("ACTIVITY").activityOrderId(ORDER).organizationId(ORG).ownerUserId(OWNER)
                .guestUserId(UUID.randomUUID()).bookingCode("AO-1").guestName("Nam").propertyName("Tour")
                .slotStartsAt(LocalDateTime.of(2026, 9, 2, 7, 0)).status("CONFIRMED").build();
    }

    // ------------------------------------------------------------- trigger-time selection

    @Test
    void hotelOnlyTriggersNeverTouchTheActivityTable() {
        service.findDueTargets(rule("BEFORE_CHECK_IN", "ALL", 24), NOW, 50);
        service.findDueTargets(rule("AFTER_CHECK_OUT", "ALL", 3), NOW, 50);

        verify(repository, times(2)).findDueHotelTargets(any(), eq(ORG), anyString(), anyInt(), eq(NOW),
                anyString(), anyInt(), anyInt());
        verify(repository, never()).findDueActivityTargets(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyInt(), anyInt());
    }

    @Test
    void beforeActivityNeverTouchesTheHotelTableEvenWhenTheRuleSaysAll() {
        // "ALL" is an audience, not a promise that both tables can answer the trigger: a stay has no
        // slot start, so asking hotel_bookings for BEFORE_ACTIVITY could only ever return nothing.
        service.findDueTargets(rule("BEFORE_ACTIVITY", "ALL", 12), NOW, 50);

        verify(repository).findDueActivityTargets(any(), eq(ORG), eq("BEFORE_ACTIVITY"), eq(12), eq(NOW),
                anyString(), eq(MarketplaceScheduledMessageServiceImpl.LOOKBACK_HOURS), eq(50));
        verify(repository, never()).findDueHotelTargets(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyInt(), anyInt());
    }

    @Test
    void onBookingConfirmedSpansBothProductLinesAndTheAudienceNarrowsIt() {
        when(repository.findDueHotelTargets(any(), any(), anyString(), anyInt(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(stay()));
        when(repository.findDueActivityTargets(any(), any(), anyString(), anyInt(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(visit()));

        List<ScheduledMessageTarget> both = service.findDueTargets(rule("ON_BOOKING_CONFIRMED", "ALL", 0), NOW, 50);
        assertEquals(2, both.size());

        service.findDueTargets(rule("ON_BOOKING_CONFIRMED", "HOTEL", 0), NOW, 50);
        service.findDueTargets(rule("ON_BOOKING_CONFIRMED", "ACTIVITY", 0), NOW, 50);
        verify(repository, times(2)).findDueHotelTargets(any(), any(), eq("ON_BOOKING_CONFIRMED"), eq(0), eq(NOW),
                anyString(), anyInt(), anyInt());
        verify(repository, times(2)).findDueActivityTargets(any(), any(), eq("ON_BOOKING_CONFIRMED"), eq(0), eq(NOW),
                anyString(), anyInt(), anyInt());
    }

    @Test
    void theOffsetAndTheJobClockAreHandedToSqlUntouched() {
        MarketplaceScheduledMessage rule = rule("BEFORE_CHECK_IN", "HOTEL", 48);
        service.findDueTargets(rule, NOW, 25);

        verify(repository).findDueHotelTargets(eq(rule.getId()), eq(ORG), eq("BEFORE_CHECK_IN"), eq(48), eq(NOW),
                eq(java.time.ZoneId.systemDefault().getId()),
                eq(MarketplaceScheduledMessageServiceImpl.LOOKBACK_HOURS), eq(25));
    }

    // ------------------------------------------------------------- one send per (rule, booking)

    @Test
    void aRunRowStopsTheSecondSendOfTheSamePair() {
        MarketplaceScheduledMessage rule = rule("BEFORE_CHECK_IN", "HOTEL", 24);
        ScheduledMessageTarget target = stay();
        // The unique index, modelled: the first reservation wins, the second is rejected.
        Set<String> reserved = new HashSet<>();
        when(repository.insertRun(any())).thenAnswer(call -> {
            MarketplaceScheduledMessageRun run = call.getArgument(0);
            if (!reserved.add(run.getScheduledMessageId() + "/" + run.getHotelBookingId())) {
                throw new DataIntegrityViolationException("uq_scheduled_run_hotel");
            }
            return 1;
        });
        when(repository.findHotelBookingStatus(BOOKING)).thenReturn(Optional.of("CONFIRMED"));
        when(chatService.start(eq(OWNER), any())).thenReturn(MarketplaceConversationResponse.builder()
                .id(UUID.randomUUID()).status(MarketplaceConversationStatus.OPEN.name()).build());
        when(chatService.send(eq(OWNER), any(), any())).thenReturn(MarketplaceMessageResponse.builder()
                .id(UUID.randomUUID()).build());

        assertEquals(MarketplaceScheduledMessageRunStatus.SENT, service.deliver(rule, target));
        assertThrows(DataIntegrityViolationException.class, () -> service.deliver(rule, target));

        // The point: the loser fails on the reservation, before any message reaches the guest.
        verify(chatService, times(1)).send(eq(OWNER), any(), any());
    }

    @Test
    void deliverySendsAsTheOwnerThroughTheNormalChatWritePathWithPlaceholdersResolved() {
        MarketplaceScheduledMessage rule = rule("BEFORE_CHECK_IN", "HOTEL", 24);
        UUID conversationId = UUID.randomUUID();
        when(repository.findHotelBookingStatus(BOOKING)).thenReturn(Optional.of("CHECKED_IN"));
        when(chatService.start(eq(OWNER), any())).thenReturn(MarketplaceConversationResponse.builder()
                .id(conversationId).status(MarketplaceConversationStatus.OPEN.name()).build());
        when(chatService.send(eq(OWNER), eq(conversationId), any())).thenReturn(
                MarketplaceMessageResponse.builder().id(UUID.randomUUID()).build());

        assertEquals(MarketplaceScheduledMessageRunStatus.SENT, service.deliver(rule, stay()));

        ArgumentCaptor<StartMarketplaceConversationRequest> start =
                ArgumentCaptor.forClass(StartMarketplaceConversationRequest.class);
        verify(chatService).start(eq(OWNER), start.capture());
        assertEquals(MarketplaceConversationType.HOTEL_BOOKING, start.getValue().getConversationType());
        assertEquals(BOOKING, start.getValue().getHotelBookingId());

        ArgumentCaptor<SendMarketplaceMessageRequest> sent =
                ArgumentCaptor.forClass(SendMarketplaceMessageRequest.class);
        verify(chatService).send(eq(OWNER), eq(conversationId), sent.capture());
        assertEquals("Chào Lan, mã đơn HB-1.", sent.getValue().getContent());
        assertTrue(sent.getValue().getClientMessageId().contains(rule.getId().toString()));
    }

    @Test
    void aBookingCancelledBetweenSelectionAndSendIsSkippedNotSent() {
        when(repository.findHotelBookingStatus(BOOKING)).thenReturn(Optional.of("CANCELLED_BY_GUEST"));

        assertEquals(MarketplaceScheduledMessageRunStatus.SKIPPED,
                service.deliver(rule("BEFORE_CHECK_IN", "HOTEL", 24), stay()));

        verify(repository).updateRunOutcome(any(), eq("SKIPPED"), anyString(), eq(null), eq(null));
        verify(chatService, never()).start(any(), any());
        verify(chatService, never()).send(any(), any(), any());
    }

    @Test
    void aBlockedOrClosedConversationIsSkippedAndTheRunRowSaysSo() {
        when(repository.findHotelBookingStatus(BOOKING)).thenReturn(Optional.of("CONFIRMED"));
        when(chatService.start(eq(OWNER), any())).thenReturn(MarketplaceConversationResponse.builder()
                .id(UUID.randomUUID()).status(MarketplaceConversationStatus.BLOCKED.name()).build());

        assertEquals(MarketplaceScheduledMessageRunStatus.SKIPPED,
                service.deliver(rule("BEFORE_CHECK_IN", "HOTEL", 24), stay()));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(repository).updateRunOutcome(any(), eq("SKIPPED"), detail.capture(), eq(null), eq(null));
        assertTrue(detail.getValue().contains("BLOCKED"));
        verify(chatService, never()).send(any(), any(), any());
    }

    @Test
    void aFailedDeliveryLeavesATerminalRunRowRatherThanRetryingForever() {
        service.recordFailure(rule("BEFORE_CHECK_IN", "HOTEL", 24), stay(), "conversation is unreachable");

        ArgumentCaptor<MarketplaceScheduledMessageRun> run =
                ArgumentCaptor.forClass(MarketplaceScheduledMessageRun.class);
        verify(repository).insertRun(run.capture());
        assertEquals("FAILED", run.getValue().getStatus());
        assertEquals(BOOKING, run.getValue().getHotelBookingId());
        verifyNoMoreInteractions(chatService);
    }
}
