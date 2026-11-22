package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.ActivityBookingResponse;
import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityBookingServiceImplRadiusSearchTest {

    private static final BigDecimal LAT = new BigDecimal("21.0285");
    private static final BigDecimal LNG = new BigDecimal("105.8542");

    @Mock
    private ActivityBookingRepository repository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private ActivityService activityService;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private ImageStorageCleanupService imageStorageCleanupService;

    private ActivityBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivityBookingServiceImpl(repository, placeRepository, tripRepository,
                activityService, exchangeRateService, imageStorageCleanupService);
    }

    @Test
    void pagesPlainRadiusSearchInSqlBeyondTheOldFetchCap() {
        List<ActivityBooking> pageRows = bookings(20, null);
        when(repository.findWithinRadius(any())).thenReturn(pageRows);

        List<ActivityBookingResponse> result = service.search(null, null, null, null, null,
                LAT, LNG, 30.0, null, null, 20, 20);

        ArgumentCaptor<ActivityBookingGeoSearchParams> params = ArgumentCaptor.forClass(ActivityBookingGeoSearchParams.class);
        verify(repository).findWithinRadius(params.capture());
        assertThat(params.getValue().getLimit()).isEqualTo(20);
        assertThat(params.getValue().getOffset()).isEqualTo(400);
        assertThat(params.getValue().getRadiusKm()).isEqualTo(30.0);
        assertThat(result).extracting(ActivityBookingResponse::getId)
                .containsExactlyElementsOf(ids(pageRows));
        verify(repository, never()).findRadiusCandidates(any());
    }

    @Test
    void destinationFilterKeepsDistanceOrderAndReachesPastTwoHundredFiftyRows() {
        // 300 candidates nearest first; even rows are in Hà Nội, odd rows in Đà Nẵng,
        // every tenth row has no destination tag (geo search keeps those).
        List<ActivityBooking> candidates = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String destinations = i % 10 == 0 ? null : (i % 2 == 0 ? "[\"Hà Nội\"]" : "[\"Đà Nẵng\"]");
            candidates.add(booking(destinations));
        }
        when(repository.findRadiusCandidates(any())).thenReturn(candidates);
        when(repository.findListByIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requested = invocation.getArgument(0);
            List<ActivityBooking> unordered = candidates.stream()
                    .filter(b -> requested.contains(b.getId()))
                    .collect(Collectors.toList());
            Collections.reverse(unordered);
            return unordered;
        });

        // Matching rows are every even index (Hà Nội or untagged): 150 of them. Page 6 of 20 = matches 120..139.
        List<ActivityBookingResponse> result = service.search(null, null, null, null, List.of("ha noi"),
                LAT, LNG, 50.0, null, null, 6, 20);

        List<UUID> expected = IntStream.range(0, 300)
                .filter(i -> i % 2 == 0)
                .skip(120)
                .limit(20)
                .mapToObj(i -> candidates.get(i).getId())
                .collect(Collectors.toList());
        assertThat(result).extracting(ActivityBookingResponse::getId).containsExactlyElementsOf(expected);

        ArgumentCaptor<ActivityBookingGeoSearchParams> params = ArgumentCaptor.forClass(ActivityBookingGeoSearchParams.class);
        verify(repository).findRadiusCandidates(params.capture());
        assertThat(params.getValue().getLimit()).isGreaterThan(250);
        assertThat(params.getValue().getOffset()).isZero();
        verify(repository, never()).findWithinRadius(any());
    }

    @Test
    void returnsNothingPastTheLastMatch() {
        when(repository.findRadiusCandidates(any())).thenReturn(bookings(5, "[\"Hà Nội\"]"));

        List<ActivityBookingResponse> result = service.search(null, null, null, null, List.of("Hà Nội"),
                LAT, LNG, 50.0, null, null, 1, 20);

        assertThat(result).isEmpty();
        verify(repository, never()).findListByIds(anyList());
    }

    private static List<ActivityBooking> bookings(int count, String destinations) {
        return IntStream.range(0, count).mapToObj(i -> booking(destinations)).collect(Collectors.toList());
    }

    private static ActivityBooking booking(String destinations) {
        return ActivityBooking.builder()
                .id(UUID.randomUUID())
                .title("Tour")
                .destinations(destinations)
                .build();
    }

    private static List<UUID> ids(List<ActivityBooking> bookings) {
        return bookings.stream().map(ActivityBooking::getId).collect(Collectors.toList());
    }
}
