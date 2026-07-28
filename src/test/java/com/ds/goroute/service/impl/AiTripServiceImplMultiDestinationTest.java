package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.AiTripDestinationRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.AiTripDraft;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.TripService;
import com.ds.goroute.thirdparty.ai.AiClient;
import com.ds.goroute.type.PlaceGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTripServiceImplMultiDestinationTest {

    private final AiTripRepository aiTripRepository = mock(AiTripRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final LocationImageRepository locationImageRepository = mock(LocationImageRepository.class);
    private final ActivityBookingRepository activityBookingRepository = mock(ActivityBookingRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final TripService tripService = mock(TripService.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AiTripServiceImpl service;
    private UUID hanoiId;
    private UUID danangId;

    @BeforeEach
    void setUp() {
        service = new AiTripServiceImpl(
                aiTripRepository,
                placeRepository,
                locationImageRepository,
                activityBookingRepository,
                activityRepository,
                tripService,
                aiClient,
                objectMapper);
        hanoiId = UUID.randomUUID();
        danangId = UUID.randomUUID();
        when(locationImageRepository.findById(hanoiId)).thenReturn(Optional.of(location(
                hanoiId, "Hà Nội", "hanoi", "21.0285", "105.8542")));
        when(locationImageRepository.findById(danangId)).thenReturn(Optional.of(location(
                danangId, "Đà Nẵng", "danang", "16.0544", "108.2022")));
        when(aiTripRepository.consumeAiTripQuota(any())).thenReturn(1);
        when(aiTripRepository.getSubscriptionTier(any())).thenReturn("FREE");
        when(aiTripRepository.getAiTripsUsed(any())).thenReturn(1);
        when(placeRepository.findForAiByDestination(
                anyString(), any(), any(), anyString(), any(), anyInt())).thenReturn(List.of());
        when(activityBookingRepository.findByDestinations(anyList(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    void generatePersistsResolvedConsecutiveRouteFromLocationImages() throws Exception {
        service.generateCandidates(request(
                destination(hanoiId, "2026-08-01", "2026-08-02", 0),
                destination(danangId, "2026-08-03", "2026-08-04", 1)), UUID.randomUUID());

        ArgumentCaptor<AiTripDraft> draftCaptor = ArgumentCaptor.forClass(AiTripDraft.class);
        verify(aiTripRepository).insertDraft(draftCaptor.capture());
        AiTripDraft draft = draftCaptor.getValue();
        assertEquals(LocalDate.parse("2026-08-01"), draft.getStartDate());
        assertEquals(LocalDate.parse("2026-08-04"), draft.getEndDate());
        assertEquals(4, draft.getDayCount());
        var route = objectMapper.readTree(draft.getDestinations());
        assertEquals(2, route.size());
        assertEquals("hanoi", route.get(0).get("citySlug").asText());
        assertEquals("danang", route.get(1).get("citySlug").asText());
        verify(placeRepository, atLeastOnce()).findForAiByDestination(
                eq("[\"hanoi\"]"), any(), any(), anyString(), any(), anyInt());
        verify(placeRepository, atLeastOnce()).findForAiByDestination(
                eq("[\"danang\"]"), any(), any(), anyString(), any(), anyInt());
    }

    @Test
    void generateRejectsOverlappingDestinationDatesBeforeConsumingQuota() {
        assertThrows(BusinessException.class, () ->
                service.generateCandidates(request(
                        destination(hanoiId, "2026-08-01", "2026-08-03", 0),
                        destination(danangId, "2026-08-03", "2026-08-04", 1)), UUID.randomUUID()));

        verify(aiTripRepository, never()).consumeAiTripQuota(any());
    }

    @Test
    void generateDoesNotFallbackWhenAiCannotRankCatalogCandidates() {
        when(placeRepository.findForAiByDestination(
                anyString(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(List.of(Place.builder()
                        .id(UUID.randomUUID())
                        .title("Museum")
                        .placeGroup(PlaceGroup.ATTRACTIONS)
                        .latitude(new BigDecimal("21.03"))
                        .longitude(new BigDecimal("105.85"))
                        .reviewRating(new BigDecimal("4.6"))
                        .reviewCount(100)
                        .build()));
        when(aiClient.completeJson(anyString(), anyString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.generateCandidates(request(
                        destination(hanoiId, "2026-08-01", "2026-08-02", 0)), UUID.randomUUID()));

        assertEquals(ErrorConstant.AI_TRIP_GENERATION_UNAVAILABLE, exception.getError().getCode());
        verify(aiTripRepository, never()).insertDraft(any());
    }

    private AiTripGenerateRequest request(AiTripDestinationRequest... destinations) {
        return AiTripGenerateRequest.builder()
                .tripName("Vietnam route")
                .cityName("Hà Nội")
                .startDate(destinations[0].getStartDate())
                .endDate(destinations[destinations.length - 1].getEndDate())
                .dayCount(4)
                .placeGroups(List.of(PlaceGroup.ATTRACTIONS))
                .destinations(List.of(destinations))
                .build();
    }

    private AiTripDestinationRequest destination(UUID id, String start, String end, int order) {
        return AiTripDestinationRequest.builder()
                .locationImageId(id)
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .orderIndex(order)
                .build();
    }

    private LocationImage location(UUID id, String name, String slug, String lat, String lng) {
        return LocationImage.builder()
                .id(id)
                .fullAddress(name)
                .citySlug(slug)
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lng))
                .build();
    }
}
