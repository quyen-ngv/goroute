package com.ds.goroute.service.notification;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TripStatus;
import com.ds.goroute.type.TransportMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripItineraryNotificationServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripMemberRepository tripMemberRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseSplitRepository expenseSplitRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScheduledNotificationSender sender;

    private TripItineraryNotificationService service;

    @BeforeEach
    void setUp() {
        service = new TripItineraryNotificationService(
                tripRepository,
                tripMemberRepository,
                activityRepository,
                expenseRepository,
                expenseSplitRepository,
                userRepository,
                sender
        );
    }

    @Test
    void sendsOneWeekReminderAtTripLocalStartTime() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(sender.sendOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
        Instant now = ZonedDateTime.of(
                trip.getStartDate().minusDays(7),
                LocalTime.of(9, 1),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        int delivered = service.processTrip(trip, now);

        assertThat(delivered).isEqualTo(1);
        verify(sender).sendOnce(
                eq(trip.getOwnerId()),
                eq(trip.getId()),
                eq(NotificationType.TRIP_STARTS_IN_ONE_WEEK),
                any(),
                any(),
                any(Map.class)
        );
    }

    @Test
    void sendsUpcomingNotificationFifteenMinutesBeforeActivity() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .dayNumber(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .name("Wat Arun")
                .placeId("place-1")
                .build();
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of(activity));
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(sender.sendOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
        Instant now = ZonedDateTime.of(
                trip.getStartDate(),
                LocalTime.of(9, 46),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        int delivered = service.processTrip(trip, now);

        assertThat(delivered).isEqualTo(1);
        verify(sender).sendOnce(
                eq(trip.getOwnerId()),
                eq(trip.getId()),
                eq(NotificationType.ITINERARY_ITEM_UPCOMING),
                any(),
                any(),
                any(Map.class)
        );
    }

    @Test
    void sendsTransportPreparationThirtyMinutesBeforeDeparture() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        Activity transport = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .dayNumber(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .name("Train to Ayutthaya")
                .category("transport")
                .transportMode(TransportMode.TRAIN)
                .address("Bang Sue Station")
                .endAddress("Ayutthaya Station")
                .build();
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of(transport));
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(sender.sendOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
        Instant now = ZonedDateTime.of(
                trip.getStartDate(),
                LocalTime.of(9, 31),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        int delivered = service.processTrip(trip, now);

        assertThat(delivered).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sender).sendOnce(
                eq(trip.getOwnerId()),
                eq(trip.getId()),
                eq(NotificationType.ITINERARY_ITEM_PREPARATION),
                any(),
                any(),
                dataCaptor.capture()
        );
        assertThat(dataCaptor.getValue())
                .containsEntry("itemKind", "transport")
                .containsEntry("transportMode", "TRAIN")
                .containsEntry("preparationLeadMinutes", 30L)
                .containsEntry("startAddress", "Bang Sue Station")
                .containsEntry("endAddress", "Ayutthaya Station");
    }

    @Test
    void sendsTransportReminderFifteenMinutesBeforeDepartureWithoutReviewPrompt() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        Activity transport = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .dayNumber(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .name("Train to Ayutthaya")
                .category("transport")
                .transportMode(TransportMode.TRAIN)
                .build();
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of(transport));
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(sender.sendOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
        Instant upcomingAt = ZonedDateTime.of(
                trip.getStartDate(),
                LocalTime.of(9, 46),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        assertThat(service.processTrip(trip, upcomingAt)).isEqualTo(1);
        verify(sender).sendOnce(
                eq(trip.getOwnerId()),
                eq(trip.getId()),
                eq(NotificationType.ITINERARY_ITEM_UPCOMING),
                any(),
                any(),
                any(Map.class)
        );

        org.mockito.Mockito.clearInvocations(sender);
        Instant completedAt = ZonedDateTime.of(
                trip.getStartDate(),
                LocalTime.of(10, 46),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();
        assertThat(service.processTrip(trip, completedAt)).isZero();
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Test
    void sendsTourAndTicketPreparationTwoHoursBeforeBooking() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        UUID bookingId = UUID.randomUUID();
        Activity tour = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .dayNumber(1)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(17, 0))
                .name("Grand Palace guided tour")
                .bookingId(bookingId)
                .bookingSource("KLOOK")
                .build();
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of(tour));
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of());
        when(sender.sendOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
        Instant now = ZonedDateTime.of(
                trip.getStartDate(),
                LocalTime.of(12, 1),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        int delivered = service.processTrip(trip, now);

        assertThat(delivered).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sender).sendOnce(
                eq(trip.getOwnerId()),
                eq(trip.getId()),
                eq(NotificationType.ITINERARY_ITEM_PREPARATION),
                any(),
                any(),
                dataCaptor.capture()
        );
        assertThat(dataCaptor.getValue())
                .containsEntry("itemKind", "booking")
                .containsEntry("bookingId", bookingId.toString())
                .containsEntry("bookingSource", "KLOOK")
                .containsEntry("preparationLeadMinutes", 120L);
    }

    @Test
    void sendsPersonalizedSummaryToEveryAcceptedMemberAndCountsUnscheduledPlaces() {
        Trip trip = trip(LocalDate.of(2026, 8, 20));
        trip.setEndDate(trip.getStartDate());
        UUID memberId = UUID.randomUUID();
        Activity unscheduledPlace = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .name("Chatuchak Market")
                .placeId("place-2")
                .build();
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .amount(new BigDecimal("1000"))
                .amountInTripCurrency(new BigDecimal("1000"))
                .paidBy(trip.getOwnerId())
                .build();
        ExpenseSplit memberSplit = ExpenseSplit.builder()
                .id(UUID.randomUUID())
                .expenseId(expense.getId())
                .userId(memberId)
                .amount(new BigDecimal("400"))
                .isSettled(false)
                .build();
        TripMember member = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .userId(memberId)
                .status(MemberStatus.ACCEPTED)
                .build();
        when(activityRepository.findByTripId(trip.getId())).thenReturn(List.of(unscheduledPlace));
        when(tripMemberRepository.findByTripId(trip.getId())).thenReturn(List.of(member));
        when(expenseRepository.findByTripId(trip.getId())).thenReturn(List.of(expense));
        when(expenseSplitRepository.findByExpenseId(expense.getId())).thenReturn(List.of(memberSplit));
        when(userRepository.findById(trip.getOwnerId())).thenReturn(java.util.Optional.of(
                User.builder().id(trip.getOwnerId()).fullName("An").build()
        ));
        when(sender.sendOnce(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2) == NotificationType.TRIP_SUMMARY);
        Instant now = ZonedDateTime.of(
                trip.getEndDate(),
                LocalTime.of(20, 5),
                ZoneId.of("Asia/Bangkok")
        ).toInstant();

        int delivered = service.processTrip(trip, now);

        assertThat(delivered).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sender, org.mockito.Mockito.times(2)).sendOnce(
                any(),
                eq(trip.getId()),
                eq(NotificationType.TRIP_SUMMARY),
                any(),
                any(),
                dataCaptor.capture()
        );
        assertThat(dataCaptor.getAllValues())
                .allSatisfy(data -> assertThat(data.get("placesCount")).isEqualTo(1L));
        assertThat(dataCaptor.getAllValues())
                .anySatisfy(data -> {
                    assertThat(data.get("outstandingAmount")).isEqualTo("400");
                    assertThat(data.get("payeeNames")).isEqualTo("An");
                });
    }

    private Trip trip(LocalDate startDate) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .name("Bangkok")
                .startDate(startDate)
                .endDate(startDate.plusDays(2))
                .timezone("Asia/Bangkok")
                .status(TripStatus.PLANNING)
                .shareExpenses(true)
                .currency("THB")
                .build();
    }
}
